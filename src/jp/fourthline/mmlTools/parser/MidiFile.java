/*
 * Copyright (C) 2017-2023 たんらる
 */

package jp.fourthline.mmlTools.parser;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.sound.midi.*;

import jp.fourthline.mabiicco.midi.InstClass;
import jp.fourthline.mmlTools.MMLEvent;
import jp.fourthline.mmlTools.MMLEventList;
import jp.fourthline.mmlTools.MMLExceptionList;
import jp.fourthline.mmlTools.MMLNoteEvent;
import jp.fourthline.mmlTools.MMLScore;
import jp.fourthline.mmlTools.MMLTempoEvent;
import jp.fourthline.mmlTools.MMLTrack;
import jp.fourthline.mmlTools.MMLVerifyException;
import jp.fourthline.mmlTools.Marker;
import jp.fourthline.mmlTools.TimeSignature;
import jp.fourthline.mmlTools.core.MMLTickTable;
import jp.fourthline.mmlTools.core.MMLTicks;
import jp.fourthline.mmlTools.core.ResourceLoader;
import jp.fourthline.mmlTools.core.MMLException;
import jp.fourthline.mmlTools.optimizer.MMLStringOptimizer;


/**
 * "*.mid" MIDIファイルの読み込み.
 */
public final class MidiFile extends AbstractMMLParser {
	private final MMLScore score = new MMLScore();
	private int resolution;

	private static final String PATCH_NAME = "mid_instPatch";
	private static final int MIDI_CHANNEL = 16;

	// Parse Option
	public static final String PARSE_TRACK_NAME = "parse.midi.trackName";
	public static final String PARSE_BEAT = "parse.midi.beat";
	public static final String PARSE_TEMPO = "parse.midi.tempo";
	public static final String PARSE_MARKER = "parse.midi.marker";
	public static final String PARSE_CONVERT_OCTAVE = "parse.midi.convertOctave";
	public static final String PARSE_CONVERT_INST = "parse.midi.convertInst";
	public static final String PARSE_MULTI_TRACK = "parse.midi.multiTrack";

	// Parse Attribute
	public static final String PARSE_ALIGN = "parse.midi.align";
	public static final String PARSE_ALIGN_1 = "parse.midi.align.1";
	public static final String PARSE_ALIGN_2 = "parse.midi.align.2";
	public static final String PARSE_ALIGN_6 = "parse.midi.align.6";
	private final Map<String, Integer> attrMap = new LinkedHashMap<>();
	private int parse_align;

	// option value
	private boolean parseTrackName;
	private boolean parseBeat;
	private boolean parseTempo;
	private boolean parseMarker;
	private boolean parseConvertOctave;
	private boolean parseConvertInst;
	private boolean parseMultiTrack;

	private void updateOptions() {
		parseTrackName = parseProperties.getOrDefault(PARSE_TRACK_NAME, false);
		parseBeat = parseProperties.getOrDefault(PARSE_BEAT, false);
		parseTempo = parseProperties.getOrDefault(PARSE_TEMPO, false);
		parseMarker = parseProperties.getOrDefault(PARSE_MARKER, false);
		parseConvertOctave = parseProperties.getOrDefault(PARSE_CONVERT_OCTAVE, true);
		parseConvertInst = parseProperties.getOrDefault(PARSE_CONVERT_INST, false);
		parseMultiTrack = parseProperties.getOrDefault(PARSE_MULTI_TRACK, false);
		System.out.println("parse_align: " + parse_align);
	}

	/* MID->programへの変換 */
	private static boolean canConvertInst = false;
	private final Map<Integer, Integer> midInstTable = new HashMap<>();

	public static void enableInstPatch () {
		canConvertInst = true;
	}

	public MidiFile() {
		// parse properties
		parseProperties = new LinkedHashMap<>();
		parseProperties.put(PARSE_TRACK_NAME, true);
		parseProperties.put(PARSE_BEAT, true);
		parseProperties.put(PARSE_TEMPO, true);
		parseProperties.put(PARSE_MULTI_TRACK, true);
		parseProperties.put(PARSE_MARKER, false);
		if (canConvertInst) {
			parseProperties.put(PARSE_CONVERT_OCTAVE, true);
			parseProperties.put(PARSE_CONVERT_INST, false);
		}

		// parse attributes
		parse_align = 1;
		attrMap.put(PARSE_ALIGN_1, 1);
		attrMap.put(PARSE_ALIGN_2, 2);
		attrMap.put(PARSE_ALIGN_6, 6);
		parseAttributes = new LinkedHashMap<>();
		parseAttributes.put(PARSE_ALIGN, attrMap.keySet());

		try {
			ResourceBundle instPatch = ResourceBundle.getBundle(PATCH_NAME, new ResourceLoader());
			for (String key : instPatch.keySet()) {
				String newInst = instPatch.getString(key).replaceAll("#.*", "");
				int keyInt = Integer.parseInt(key.trim());
				int newInstInt = Integer.parseInt(newInst.trim());
				System.out.println("[MID-PATCH] " + keyInt + " -> " + newInstInt);
				midInstTable.put(keyInt, newInstInt);
			}
		} catch (MissingResourceException e) {}
	}

	@Override
	public String getName() {
		return "MIDI";
	}

	@Override
	public MMLScore parse(InputStream istream) throws MMLParseException {
		updateOptions();
		try {
			BufferedInputStream bin = new BufferedInputStream(istream);
			MidiFileFormat format = MidiSystem.getMidiFileFormat(bin);
			int formatType = format.getType();
			System.out.println("type: " + formatType);

			bin.reset();
			Sequence seq = MidiSystem.getSequence(bin);
			resolution = seq.getResolution();
			System.out.println(seq.getTracks().length);
			System.out.println("resolution: "+resolution);
			System.out.println(seq.getDivisionType());
			System.out.println(seq.getMicrosecondLength());
			System.out.println(seq.getTickLength());
			bin.close();

			if (formatType == 0) {
				parseFormat0Track(seq.getTracks()[0]);
			} else if (formatType == 1) {
				parseFormat1Track(seq.getTracks());
			} else {
				throw new MMLParseException("not support format <" + formatType + ">");
			}
		} catch (InvalidMidiDataException | IOException e) {
			e.printStackTrace();
		}

		score.getTempoEventList().addAll(tempoList);
		try {
			return score.generateAll();
		} catch (MMLExceptionList | MMLVerifyException e) {
			return score;
		}
	}

	private final HashMap<Integer, MMLNoteEvent> activeNoteMap = new HashMap<>();
	private final ArrayList<MMLNoteEvent> curNoteList = new ArrayList<>();
	private final ArrayList<MMLTempoEvent> tempoList = new ArrayList<>();

	private static final class TrackInfo {
		private String name;
		private int panpot = 64;
		private int program = 0;
		private TrackInfo(int count) {
			name = "Track"+count;
		}
		private MMLTrack createMMLTrack() {
			MMLTrack track = new MMLTrack();
			track.setTrackName(name);
			track.setPanpot(panpot);
			track.setProgram(program);
			return track;
		}
		private void setName(String name) {
			if ( (name != null) && (name.length() > 0) ) {
				this.name = name;
			}
		}
		private void setProgram(int data) {
			this.program = data;
		}
	}

	private List<MidiEvent> convMidiEventList(Track track) {
		ArrayList<MidiEvent> midiEventList = new ArrayList<>(track.size());
		for (int i = 0; i < track.size(); i++) {
			midiEventList.add(track.get(i));
		}
		return midiEventList;
	}

	/**
	 * format0 のトラックを読み取る
	 * @param track
	 * @throws MMLParseException
	 */
	private void parseFormat0Track(Track track) throws MMLParseException {
		var midiEventList = convMidiEventList(track);
		List<List<MidiEvent>> chList = new ArrayList<>();
		for (int i = 0; i < MIDI_CHANNEL; i++) {
			chList.add(new ArrayList<>());
		}

		TrackInfo trackInfo = new TrackInfo(1);
		for (MidiEvent event : midiEventList) {
			MidiMessage msg = event.getMessage();
			long tick = convTick( event.getTick() );
			if (tick >= MMLEvent.MAX_TICK) continue;
			if (msg instanceof MetaMessage) {
				parseMetaMessage((MetaMessage)msg, tick, trackInfo);
			} else if (msg instanceof ShortMessage shortmsg) {
				int channel = shortmsg.getChannel();
				chList.get(channel).add(event);
			} else if (msg instanceof SysexMessage) {
				System.out.println("Sysex");
			} else {
				throw new MMLParseException("Unknown MIDI message.");
			}
		}

		// チャンネルごとの情報を読み取る
		for (int i = 0; i < MIDI_CHANNEL; i++) {
			activeNoteMap.clear();
			curNoteList.clear();
			trackInfo = new TrackInfo(i+1);
			for (MidiEvent event : chList.get(i)) {
				MidiMessage msg = event.getMessage();
				long tick = convTick( event.getTick() );
				if (tick >= MMLEvent.MAX_TICK) continue;
				if (msg instanceof ShortMessage shortmsg) {
					parseShortMessage(shortmsg, tick, trackInfo);
				}
			}

			// MMLEventListのリストを使ってトラックを生成.
			createMMLTrack(createMMLEventList(), trackInfo);
		}
	}

	/**
	 * format1 のトラックを読み取る
	 * @param track
	 * @param count
	 * @throws MMLParseException
	 */
	private void parseFormat1Track(Track track[]) throws MMLParseException {
		for (int i = 0; i < track.length; i++) {
			TrackInfo trackInfo = new TrackInfo(i+1);
			activeNoteMap.clear();
			curNoteList.clear();
			System.out.println(" - track -");
			System.out.println(track[i].size());

			for (MidiEvent event : convMidiEventList(track[i])) {
				MidiMessage msg = event.getMessage();
				long tick = convTick( event.getTick() );
				if (tick >= MMLEvent.MAX_TICK) continue;
				if (msg instanceof MetaMessage) {
					parseMetaMessage((MetaMessage)msg, tick, trackInfo);
				} else if (msg instanceof ShortMessage) {
					parseShortMessage((ShortMessage)msg, tick, trackInfo);
				} else if (msg instanceof SysexMessage) {
					System.out.println("Sysex");
				} else {
					throw new MMLParseException("Unknown MIDI message.");
				}
			}

			// MMLEventListのリストを使ってトラックを生成.
			createMMLTrack(createMMLEventList(), trackInfo);
		}
	}

	/**
	 * 整列済みノートイベントからMMLTrackをつくる
	 * @param eventList
	 * @param trackInfo
	 */
	private void createMMLTrack(ArrayList<MMLEventList> eventList, TrackInfo trackInfo) {
		System.out.printf(" ###### track tick: %d %d => %d\n",
				activeNoteMap.size(),
				curNoteList.size(),
				eventList.size());
		try {
			while (eventList.size() > 0) {
				String[] mml = new String[3];
				for (int i = 0; i < mml.length; i++) {
					if (!eventList.isEmpty()) {
						mml[i] = new MMLStringOptimizer(eventList.get(0).getInternalMMLString()).toString();
						eventList.remove(0);
					} else {
						mml[i] = "";
					}
				}
				MMLTrack track = trackInfo.createMMLTrack();
				track.setMML(mml[0], mml[1], mml[2], "");
				score.addTrack(track);
				if (!parseMultiTrack) {
					break;
				}
			}
		} catch (MMLExceptionList e) {
			e.printStackTrace();
		}
	}

	/**
	 * 取り込んだノートイベントから重複しないMMLEventListを生成する.
	 * @return
	 */
	private ArrayList<MMLEventList> createMMLEventList() {
		ArrayList<MMLEventList> eventList = new ArrayList<>();

		for (MMLNoteEvent noteEvent : curNoteList) {
			for (MMLEventList e : eventList) {
				if (!e.isOverlapNote(noteEvent)) {
					e.addMMLNoteEvent(noteEvent);
					noteEvent = null;
					break;
				}
			}
			if (noteEvent != null) {
				MMLEventList e = new MMLEventList("");
				e.addMMLNoteEvent(noteEvent);
				eventList.add(e);
			}
		}

		eventList.forEach(t -> t.deleteMinRest());

		return eventList;
	}

	/**
	 * メタメッセージ
	 * @param msg
	 * @param tick
	 * @param trackInfo
	 */
	private void parseMetaMessage(MetaMessage msg, long tick, TrackInfo trackInfo) {
		System.out.print(tick+" > ");
		int type = msg.getType();
		byte[] data = msg.getData();
		switch (type) {
		case MMLTempoEvent.META: // テンポ
			ByteBuffer buf = ByteBuffer.allocate(4);
			buf.put((byte)0);
			buf.put(data);
			int tempo = 60000000/buf.getInt(0);
			System.out.println("Tempo: "+tempo);
			if (parseTempo) {
				new MMLTempoEvent(tempo, (int)tick).appendToListElement(tempoList);
			}
			break;
		case 3: // シーケンス名/トラック名
			String name = new String(data);
			System.out.println("Name: "+name);
			if (parseTrackName) {
				trackInfo.setName(name);
			}
			break;
		case 1: // テキストイベント
			System.out.println("Text: "+new String(data));
			break;
		case 2: // 著作権表示
			System.out.println("(C): "+new String(data));
			break;
		case Marker.META: // マーカー
			String s = new String(data);
			System.out.println("Marker: "+s);
			if (parseMarker) {
				score.getMarkerList().add(new Marker(s, (int) tick));
			}
			break;
		case 4: // 楽器名
		case 5: // 歌詞
		case 7: // キューポイント
			System.out.println("Text(" + type + "): "+new String(data));
			break;
		case 0x58: // 拍子/メトロノーム設定
			System.out.printf("met: %d %d %d %d\n", data[0], 1<<data[1], data[2], data[3]);
			if (parseBeat) {
				int base = 1 << data[1];
				int timeCount = data[0];
				if ((base <= 32) && (timeCount > 0) && (timeCount <= 32)) {
					if (tick == 0) {
						score.setBaseOnly(base);
						score.setTimeCountOnly(timeCount);
					} else {
						try {
							score.addTimeSignature(new TimeSignature(score, (int) tick, timeCount, base));
						} catch (MMLException e) {
							e.printStackTrace();
						}
					}
				}
			}
			break;
		case 0x59: // 調号
			System.out.printf("sig: %d %d\n", data[0], data[1]);
			break;
		default:
			System.out.printf("Meta: [%x] [%d]\n", type, data.length);
			break;
		}
	}

	/**
	 * ショートメッセージ
	 * @param msg
	 * @param tick
	 * @param trackInfo
	 * @throws MMLParseException
	 */
	private void parseShortMessage(ShortMessage msg, long tick, TrackInfo trackInfo) throws MMLParseException {
		int command = msg.getCommand();
		int channel = msg.getChannel();
		int data1 = msg.getData1();
		int data2 = msg.getData2();
		switch (command) {
		case ShortMessage.CONTROL_CHANGE:
			if (data1 == 10) { // panpot
				trackInfo.panpot = data2;
			}
			System.out.printf("control change: [%d] [%d]\n", data1, data2);
			break;
		case ShortMessage.NOTE_ON:
			if (data2 > 0) {
				int note = data1 - (parseConvertOctave ? 12 : 0);
				int velocity = data2 / 8;
				if (!activeNoteMap.containsKey(note)) {
					MMLNoteEvent noteEvent = new MMLNoteEvent(note, 0, (int)tick, velocity);
					try {
						noteEvent.toMMLString();
					} catch (MMLException e) {
						// ノートが範囲外すぎるなどして, MML変換できない場合は無視.
						break;
					}
					activeNoteMap.put(note, noteEvent);
					curNoteList.add(noteEvent);
				}
				break;
			}
			// data2 == 0 は Note Off.
		case ShortMessage.NOTE_OFF:
			int note = data1 - (parseConvertOctave ? 12 : 0);
			MMLNoteEvent noteEvent = activeNoteMap.get(note);
			if (noteEvent != null) {
				tick -= noteEvent.getTickOffset();
				if (tick < MMLTicks.minimumTick()) {
					tick = MMLTicks.minimumTick();
				}
				noteEvent.setTick( (int)tick );
				activeNoteMap.remove(note);
			}
			break;
		case ShortMessage.PROGRAM_CHANGE:
			System.out.printf("program change: [%d] [%d] (%d)\n", data1, data2, channel);
			if (!canConvertInst) {
				if (channel == 9) {
					trackInfo.setProgram(InstClass.DRUM);
				} else {
					trackInfo.setProgram(data1);
				}
			} else if (parseConvertInst && midInstTable.containsKey(data1)) {
				data1 = midInstTable.get(data1);
				trackInfo.setProgram(data1);
				System.out.println("   -> " + data1);
			}
			break;
		default:
			System.out.printf("short: [%x] [%d] [%d] [%d]\n", command, channel, data1, data2);
		}
	}

	/**
	 * Tick変換
	 * @param tick
	 * @return
	 */
	private long convTick(long tick) {
		long value = (tick * MMLTickTable.TPQN / resolution);
		if (parse_align > 1) {
			value += (parse_align/2);
			value -= value % parse_align;
		}
		return value;
	}

	@Override
	public void setParseAttribute(String key, String value) {
		System.out.println("setParseAttribute "+key+":"+value);
		if (key == PARSE_ALIGN) {
			parse_align = attrMap.get(value);
		}
	}

	public static void main(String[] args) {
		try {
			MMLScore score = new MidiFile().parse(new FileInputStream("sample2.mid"));
			score.generateAll();
		} catch (FileNotFoundException | MMLParseException | MMLExceptionList | MMLVerifyException e) {
			e.printStackTrace();
		}
	}
}
