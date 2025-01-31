/*
 * Copyright (C) 2021-2023 たんらる
 */
package jp.fourthline.mabiicco.ui;

import static jp.fourthline.mabiicco.AppResource.appText;

import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.function.IntConsumer;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

import jp.fourthline.mabiicco.IEditState;
import jp.fourthline.mabiicco.MabiIccoProperties;
import jp.fourthline.mabiicco.midi.MabiDLS;
import jp.fourthline.mmlTools.MMLScore;

public final class PianoRollScaler implements MouseWheelListener {
	private final IMMLManager mmlManager;
	private final PianoRollView pianoRollView;
	private final JScrollPane scrollPane;
	private final MainView parent;
	private final IEditState editState;

	private int viewScaleIndex = 0;
	private final double[] viewScaleTable = { 6, 5, 4, 3, 2, 1.5, 1, 0.75, 0.5, 0.375, 0.25, 0.1 };

	public PianoRollScaler(IMMLManager mmlManager, PianoRollView pv, JScrollPane sp, MainView parent, IEditState editState) {
		this.mmlManager = mmlManager;
		this.pianoRollView = pv;
		this.scrollPane = sp;
		this.parent = parent;
		this.editState = editState;
		pv.addMouseWheelListener(this);
	}

	public double getScale() {
		return viewScaleTable[viewScaleIndex];
	}

	public void expandPianoViewWide() {
		scalePlayPosition(t -> this.expandPianoViewWide(t));
	}

	public void reducePianoViewWide() {
		scalePlayPosition(t -> this.reducePianoViewWide(t));
	}

	/**
	 * 再生時は, 再生位置を基準に拡大/縮小する
	 * @param f
	 */
	private void scalePlayPosition(IntConsumer f) {
		int xOffset = 0;
		if (MabiDLS.getInstance().getSequencer().isRunning()) {
			double scale = pianoRollView.getWideScale();
			long position = pianoRollView.getSequencePlayPosition();
			int x = scrollPane.getViewport().getViewPosition().x;
			xOffset = (int)(position / scale) - x;
		}
		f.accept(xOffset);
	}

	/**
	 * ピアノロールビューの表示を1段階拡大します.
	 * @param xOffset 拡大基準
	 */
	public void expandPianoViewWide(int xOffset) {
		if (viewScaleIndex+1 < viewScaleTable.length) {
			viewScaleIndex++;
			updatePianoViewWide(xOffset);
		}
	}

	/**
	 * ピアノロールビューの表示を1段階縮小します.
	 * @param xOffset 縮小基準
	 */
	public void reducePianoViewWide(int xOffset) {
		if (viewScaleIndex-1 >= 0) {
			viewScaleIndex--;
			updatePianoViewWide(xOffset);
		}
	}

	private void updatePianoViewWide(int xOffset) {
		double scale1 = pianoRollView.getWideScale();
		pianoRollView.setWideScale(viewScaleTable[viewScaleIndex]);
		repositionChangeScaleView(scale1, pianoRollView.getWideScale(), xOffset);
	}

	// TODO: 応急措置, 拡大時に表示位置を保持できていない.
	private void repositionChangeScaleView(double scale1, double scale2, int xOffset) {
		JViewport viewport = scrollPane.getViewport();
		Point p = viewport.getViewPosition();

		// 拡大/縮小したときの表示位置を調整します.
		p.x = (int)((p.x + xOffset) * scale1 / scale2) - xOffset;
		parent.repaint();
		viewport.updateUI();
		viewport.setViewPosition(p);
		if ( (viewport.getViewPosition().x != p.x) || (viewport.getViewPosition().y != p.y)) {
			viewport.setViewPosition(p);
		}
	}

	/**
	 * マウスホイール横スクロール量
	 */
	public enum MouseScrollWidth implements SettingButtonGroupItem {
		AUTO {
			@Override
			public int getDelta(MMLScore score, JViewport viewport, PianoRollView pianoRollView) {
				int measureWidth = pianoRollView.convertTicktoX(score.getMeasureTick());
				int viewportWidth = viewport.getWidth();
				if (viewportWidth > measureWidth) {
					return pianoRollView.convertTicktoX((viewportWidth > measureWidth * 2) ? score.getMeasureTick() : score.getBeatTick());
				}
				return FIXW;
			}
		},
		MEASURE {
			@Override
			public int getDelta(MMLScore score, JViewport viewport, PianoRollView pianoRollView) {
				return pianoRollView.convertTicktoX(score.getMeasureTick());
			}
			
		},
		BEAT {
			@Override
			public int getDelta(MMLScore score, JViewport viewport, PianoRollView pianoRollView) {
				return pianoRollView.convertTicktoX(score.getBeatTick());
			}
		},
		FIX6 {
			@Override
			public int getDelta(MMLScore score, JViewport viewport, PianoRollView pianoRollView) {
				return FIXW;
			}
		};

		private final static int FIXW = 6;
		private final String name;
		private MouseScrollWidth() {
			this.name = appText("ui.mouse_scroll_width." + super.name().toLowerCase());
		}

		@Override
		public String getButtonName() {
			return this.name;
		}

		public abstract int getDelta(MMLScore score, JViewport viewport, PianoRollView pianoRollView);
	}

	private void scrollH(int rotation, JViewport viewport, Point p) {
		// 横方向の移動
		var score = mmlManager.getMMLScore();
		int delta = MabiIccoProperties.getInstance().mouseScrollWidth.get().getDelta(score, viewport, pianoRollView);
		p.x += (rotation > 0) ? delta : -delta;
		p.x = Math.min(Math.max(0, p.x), pianoRollView.getWidth() - viewport.getWidth());
		scrollPane.getViewport().setViewPosition(p);
		parent.repaint();
	}

	private void scrollV(int rotation, JViewport viewport, Point p) {
		// 縦方向の移動
		int delta = pianoRollView.getNoteHeight() * 2;
		p.y += (rotation > 0) ? delta : -delta;
		p.y = Math.min(Math.max(0, p.y + ((rotation > 0) ? delta : -delta)), pianoRollView.getHeight() - viewport.getHeight());
		scrollPane.getViewport().setViewPosition(p);
		parent.repaint();
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		JViewport viewport = scrollPane.getViewport();
		Point p = viewport.getViewPosition();
		int rotation = e.getWheelRotation();
		if (e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
			// 編集アクションの実行
			editState.notesModifyVelocity(e.getPoint(), rotation < 0);
		} else if (!e.isAltDown() && e.isControlDown() && !e.isShiftDown()) {
			// 幅の拡大縮小
			if (rotation < 0) {
				expandPianoViewWide( e.getX() - p.x );
			} else {
				reducePianoViewWide( e.getX() - p.x );
			}
		} else if (!e.isAltDown() && !e.isControlDown() && e.isShiftDown()) {
			// 横方向の移動
			scrollH(rotation, viewport, p);
		} else {
			// 縦方向の移動
			scrollV(rotation, viewport, p);
		}
	}
}
