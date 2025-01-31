/*
 * Copyright (C) 2013-2023 たんらる
 */

package jp.fourthline.mabiicco.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import javax.sound.midi.Sequencer;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import jp.fourthline.mabiicco.ActionDispatcher;
import jp.fourthline.mabiicco.MabiIccoProperties;
import jp.fourthline.mabiicco.midi.MabiDLS;
import jp.fourthline.mabiicco.ui.color.ColorManager;
import jp.fourthline.mabiicco.ui.color.ColorSet;
import jp.fourthline.mabiicco.ui.editor.IEditAlign;
import jp.fourthline.mabiicco.ui.editor.IMarkerEditor;
import jp.fourthline.mabiicco.ui.editor.MMLTempoEditor;
import jp.fourthline.mabiicco.ui.editor.MarkerEditor;
import jp.fourthline.mabiicco.ui.editor.StartOffsetEditor;
import jp.fourthline.mabiicco.ui.editor.TimeSignatureEditor;
import jp.fourthline.mmlTools.MMLEvent;
import jp.fourthline.mmlTools.MMLEventList;
import jp.fourthline.mmlTools.MMLNoteEvent;
import jp.fourthline.mmlTools.MMLScore;
import jp.fourthline.mmlTools.MMLTempoEvent;
import jp.fourthline.mmlTools.Marker;
import jp.fourthline.mmlTools.TimeSignature;
import jp.fourthline.mmlTools.core.MMLException;


public final class ColumnPanel extends JPanel implements MouseListener, MouseMotionListener, IViewTargetMarker {
	private static final long serialVersionUID = -6609938350741425221L;

	private static final ColorSet BEAT_BORDER_COLOR = ColorSet.create(new Color(0.4f, 0.4f, 0.4f), new Color(128, 128, 128));
	private static final Color TEMPO_MAKER_FILL_COLOR = new Color(0.4f, 0.8f, 0.8f);
	private static final Color MAKER_FILL_COLOR = new Color(0.2f, 0.8f, 0.2f);
	private static final Color TIME_SIGNATURE_FILL_COLOR = new Color(255, 165, 0);
	private static final Color TARGET_MAKER_FILL_COLOR = new Color(0.9f, 0.7f, 0.0f, 0.6f);
	private static final ColorSet START_COMMON_OFFSET_COLOR = ColorSet.create(new Color(255, 167, 227), Color.decode("#993366"));
	private static final ColorSet START_OFFSET_COLOR = ColorSet.create(new Color(255, 202, 227), Color.decode("#996666"));
	private static final ColorSet TEXT_COLOR = ColorSet.create(Color.DARK_GRAY, Color.LIGHT_GRAY);
	private static final int DRAW_HEIGHT = 32;
	private static final int DRAW_OFFSET_HEIGHT = 6;
	private static final int DRAW_HEIGHT_ERR_BAR = 10;

	private final PianoRollView pianoRollView;
	private final IMMLManager mmlManager;
	private final IEditAlign editAlign;

	private final JPopupMenu popupMenu = new JPopupMenu();
	private final ArrayList<IMarkerEditor> markerEditor = new ArrayList<>();

	private final MabiDLS dls = MabiDLS.getInstance();
	private final MabiIccoProperties appProperties = MabiIccoProperties.getInstance();

	private OptionalInt targetMarker = OptionalInt.empty();

	public ColumnPanel(Frame parentFrame, PianoRollView pianoRollView, IMMLManager mmlManager, IEditAlign editAlign) {
		super();
		this.pianoRollView = pianoRollView;
		this.mmlManager = mmlManager;
		this.editAlign = editAlign;
		addMouseListener(this);
		addMouseMotionListener(this);

		var tempoEditor = new MMLTempoEditor(parentFrame, mmlManager, editAlign, this);
		markerEditor.add( tempoEditor );
		markerEditor.add( new MarkerEditor(parentFrame, mmlManager, editAlign, this) );
		markerEditor.add( new TimeSignatureEditor(parentFrame, mmlManager, editAlign, this) );
		markerEditor.add( new StartOffsetEditor(parentFrame, mmlManager, editAlign, this) );

		// popupMenu に各MenuItemを登録する.
		markerEditor.forEach(t -> t.getMenuItems().forEach(popupMenu::add));

		popupMenu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {
				PaintOff();
				pianoRollView.repaint();
			}
		});

		// laf変更反映対応
		ActionDispatcher.getInstance().addUpdateUIComponent(popupMenu);
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(getWidth(), DRAW_HEIGHT+(DRAW_OFFSET_HEIGHT*2));
	}

	@Override
	public int getWidth() {
		return pianoRollView.getWidth();
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g.create();

		paintStartOffset(g2);
		paintErr(g2);
		paintRuler(g2);
		paintMarker(g2);
		paintTempoEvents(g2);
		paintTimeSignature(g2);
		pianoRollView.paintSequenceLine(g2, getHeight());
		paintTargetMarker(g2);

		if (appProperties.viewVelocityLine.get()) {
			paintVelocityLine(g2);
		}

		g2.dispose();
	}

	private static final float[] dash = { 2.0f, 4.0f };
	private static final BasicStroke dashStroke = new BasicStroke(1.0f, 
			BasicStroke.CAP_BUTT, 
			BasicStroke.JOIN_MITER, 
			10.0f, 
			dash, 
			0.0f);
	/**
	 * スタート位置未満の背景
	 */
	private void paintStartOffset(Graphics2D g) {
		int width = getWidth();
		var oldStroke = g.getStroke();
		g.setStroke(dashStroke);
		g.setColor(Color.GRAY);
		g.drawLine(0, DRAW_HEIGHT, width, DRAW_HEIGHT);
		g.setStroke(oldStroke);

		int x1 = pianoRollView.convertTicktoX(mmlManager.getActiveTrack().getCommonStartOffset());
		int x2 = pianoRollView.convertTicktoX(mmlManager.getActiveMMLPartStartOffset());

		g.setColor(START_COMMON_OFFSET_COLOR.get());
		g.fillRect(0, DRAW_HEIGHT, x1, DRAW_OFFSET_HEIGHT);

		g.setColor(START_OFFSET_COLOR.get());
		g.fillRect(0, DRAW_HEIGHT+DRAW_OFFSET_HEIGHT, x2, DRAW_OFFSET_HEIGHT);
	}

	/**
	 * エラー箇所の表示
	 */
	private void paintErr(Graphics2D g) {
		var errList = mmlManager.getMMLScore().getMMLErr();
		g.setColor(Color.RED);
		for (var item : errList) {
			int x = pianoRollView.convertTicktoX(item.getNote().getTickOffset());
			int width = Math.max(pianoRollView.convertTicktoX(item.getNote().getTick()), 2);
			g.fillRect(x, 1, width, DRAW_HEIGHT_ERR_BAR);
		}
	}
	/**
	 * ルーラを表示します。
	 */
	private void paintRuler(Graphics2D g) {
		MMLScore score = mmlManager.getMMLScore();
		var timeSignatureIterator = score.getTimeSignatureList().iterator();
		int measureTick = score.getTimeCountOnly() * score.getBeatTick();
		int m = 0;
		int md = 0;
		int y2 = getHeight();
		long length = pianoRollView.convertXtoTick( getWidth() );
		g.setColor(BEAT_BORDER_COLOR.get());

		int nextMeasureOffset = 0;
		TimeSignature timeSignature = timeSignatureIterator.hasNext() ? timeSignatureIterator.next() : null;
		if (timeSignature != null) {
			nextMeasureOffset = timeSignature.getMeasureOffset();
		}
		while (md < length) {
			int x = pianoRollView.convertTicktoX(md);
			int y1 = 0;
			g.drawLine(x, y1, x, y2);
			g.drawString(Integer.toString(m), x+2, y1+10);

			if ( (timeSignature != null) && (m >= nextMeasureOffset) ) {
				measureTick = timeSignature.getNumTime() * timeSignature.getBaseTick();
				timeSignature = timeSignatureIterator.hasNext() ? timeSignatureIterator.next() : null;
				if (timeSignature != null) {
					nextMeasureOffset = timeSignature.getMeasureOffset();
				}
			}
			m++;
			md += measureTick;
		}
	}

	/**
	 * テンポを表示します.
	 */
	private void paintTempoEvents(Graphics2D g) {
		if (appProperties.enableViewTempo.get()) {
			for (MMLTempoEvent tempoEvent : mmlManager.getMMLScore().getTempoEventList()) {
				drawMarker(g, tempoEvent, TEMPO_MAKER_FILL_COLOR, 0);
			}
		}
	}

	private void paintMarker(Graphics2D g) {
		if (appProperties.enableViewMarker.get()) {
			for (Marker marker : mmlManager.getMMLScore().getMarkerList()) {
				drawMarker(g, marker, MAKER_FILL_COLOR, -11);
			}
		}
	}

	private void paintTimeSignature(Graphics2D g) {
		for (TimeSignature ts : mmlManager.getMMLScore().getTimeSignatureList()) {
			drawMarker(g, ts, TIME_SIGNATURE_FILL_COLOR, 11);
		}
	}

	private void drawMarker(Graphics2D g, MMLEvent event, Color color, int dy) {
		try {
			String s = event.toMMLString();
			int x = pianoRollView.convertTicktoX(event.getTickOffset());
			int[] xPoints = { x-3, x+3, x+3, x, x-3 };
			int[] yPoints = { -10, -10, -4, -1, -4 };
			for (int i = 0; i < yPoints.length; i++) {
				yPoints[i] += DRAW_HEIGHT + dy;
			}

			// label
			g.setColor(TEXT_COLOR.get());
			g.drawString(s, x+6, DRAW_HEIGHT-2+dy);

			// icon
			g.setColor(color);
			g.fillPolygon(xPoints, yPoints, xPoints.length);
			g.setColor(BEAT_BORDER_COLOR.get());
			g.drawPolygon(xPoints, yPoints, xPoints.length);
		} catch (MMLException e) {
			e.printStackTrace();
		}
	}

	private void paintTargetMarker(Graphics2D g) {
		if (targetMarker.isEmpty()) {
			return;
		}

		int x = pianoRollView.convertTicktoX( targetMarker.getAsInt() );
		int[] xPoints = { x-5, x+5, x+5, x, x-5 };
		int[] yPoints = { 8, 8, DRAW_HEIGHT-5, DRAW_HEIGHT, DRAW_HEIGHT-5 };

		// icon
		g.setColor(TARGET_MAKER_FILL_COLOR);
		g.fillPolygon(xPoints, yPoints, xPoints.length);
		g.setColor(BEAT_BORDER_COLOR.get());
		g.drawPolygon(xPoints, yPoints, xPoints.length);
	}

	private void paintVelocityLine(Graphics2D g) {
		MMLEventList activePart = mmlManager.getActiveMMLPart();
		if (activePart == null) {
			return;
		}

		int trackIndex = mmlManager.getActiveTrackIndex();
		Color rectColor = ColorManager.defaultColor().getActiveRectColor(trackIndex);
		g.setColor(rectColor);

		for (MMLNoteEvent noteEvent : activePart.getMMLNoteEventList()) {
			int x = pianoRollView.convertTicktoX( noteEvent.getTickOffset() );
			int width = pianoRollView.convertTicktoX( noteEvent.getTick() );
			int velocity = noteEvent.getVelocity();
			if (velocity < 0)  velocity = 0;
			if (velocity > 15) velocity = 15;
			int y = DRAW_HEIGHT - velocity - 2;
			g.drawLine(x, y, x+width-1, y);
		}
	}

	private void setSequenceBar(int x) {
		Sequencer sequencer = dls.getSequencer();
		if (!sequencer.isRunning()) {
			long tick = pianoRollView.convertXtoTick(x);
			tick -= tick % editAlign.getEditAlign();
			pianoRollView.setSequenceTick(tick);
			repaint();
			pianoRollView.repaint();
		} else {
			long tick = pianoRollView.convertXtoTick(x);
			// 移動先のテンポに設定する.
			int tempo = mmlManager.getMMLScore().getTempoOnTick(tick);
			sequencer.setTickPosition(tick);
			sequencer.setTempoInBPM(tempo);
			System.out.printf("Sequence update: tick(%d), tempo(%d)\n", tick, tempo);
		}
	}

	private void playAllNoteOnTick(int x) {
		Sequencer sequencer = dls.getSequencer();
		if (!appProperties.enableClickPlay.get()) {
			return;
		}
		if (!sequencer.isRunning()) {
			MMLScore score = mmlManager.getMMLScore();
			long tick = pianoRollView.convertXtoTick(x);
			int trackIndex = 0;
			List<MMLNoteEvent[]> noteListArray = score.getNoteListOnTickOffset(tick);
			for (MMLNoteEvent[] noteList : noteListArray) {
				int partIndex = 0;
				for (MMLNoteEvent noteEvent : noteList) {
					if (x < 0) {
						dls.playNotes(score, null, trackIndex, partIndex);
					} else {
						dls.playNotes(score, new MMLNoteEvent[] { noteEvent }, trackIndex, partIndex);
					}
					partIndex++;
				}
				trackIndex++;
			}
		}
	}

	private void popupAction(Component component, int x, int y) {
		int targetTick = (int)pianoRollView.convertXtoTick(x);
		int delta = (int)pianoRollView.convertXtoTick(6);

		// クリックした位置に、テンポ/マーカー イベントがあれば削除モードになります.
		markerEditor.forEach(t -> t.activateEditMenuItem(targetTick, delta));

		popupMenu.show(component, x, y);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();

		if (SwingUtilities.isLeftMouseButton(e)) {
		} else if (SwingUtilities.isRightMouseButton(e)) {
			popupAction(e.getComponent(), x, y);
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {
		int x = e.getX();

		if (SwingUtilities.isLeftMouseButton(e)) {
			setSequenceBar(x);
			playAllNoteOnTick(x);
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (SwingUtilities.isLeftMouseButton(e)) {
			playAllNoteOnTick(-1);
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		if (SwingUtilities.isLeftMouseButton(e)) {
			playAllNoteOnTick(x);
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {}

	@Override
	public void PaintOnTarget(int tickOffset) {
		this.targetMarker = OptionalInt.of( tickOffset );
		repaint();
	}

	@Override
	public void PaintOff() {
		this.targetMarker = OptionalInt.empty();
		repaint();
	}
}
