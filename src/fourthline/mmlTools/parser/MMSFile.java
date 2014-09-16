/*
 * Copyright (C) 2013-2014 たんらる
 */

package fourthline.mmlTools.parser;

import java.io.InputStream;
import java.util.List;

import fourthline.mmlTools.MMLScore;
import fourthline.mmlTools.MMLTrack;

/**
 * まきまびしーくさんのファイルフォーマットを扱います.
 * @author fourthline
 *
 */
public final class MMSFile implements IMMLFileParser {
	private final MMLScore score = new MMLScore();

	@Override
	public MMLScore parse(InputStream istream) throws MMLParseException {
		List<SectionContents> contentsList = SectionContents.makeSectionContentsByInputStream(istream, "Shift_JIS");
		if (contentsList.isEmpty()) {
			throw(new MMLParseException());
		}

		for (SectionContents section : contentsList) {
			String sectionName = section.getName();

			if ( sectionName.matches("\\[part[0-9]+\\]") ) {
				/* MMLパート */
				System.out.println("part");
				MMLTrack track = parseMMSPart(section.getContents());
				System.out.println(track.getMML());
				System.out.println(track.getProgram());
				score.addTrack(track);
			} else if (sectionName.equals("[infomation]")) {
				parseInfomation(section.getContents());
			}
		}

		return score;
	}

	/* MMS->programへの変換テーブル */
	static final int mmsInstTable[] = {
		0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 
		10, 11, 12, 13, 14, 15, 16, 17, 18, 66, 
		67, 68,	69, 70, 71, 72, 73, 74, 75, 76, 
		18
	};

	/**
	 * mmsファイルのinstrument値は、DLSのものではないので変換を行います.
	 * @param mmsInst
	 * @return DLSのprogram値
	 */
	private int convertInstProgram(int mmsInst) {
		return mmsInstTable[mmsInst];
	}

	private void parseInfomation(String contents) {
		final String rythm[] = { "4", "4" };
		for (String s : contents.split("\n")) {
			TextParser textParser = TextParser.text(s);
			if ( textParser.startsWith("title=",     t -> score.setTitle(t)) ) {
			} else if ( textParser.startsWith("auther=",    t -> score.setAuthor(t)) ) {
			} else if ( textParser.startsWith("rythmNum=",  t -> rythm[0] = t) ) {
			} else if ( textParser.startsWith("rythmBase=", t -> rythm[1] = t) ) {
			}
		}
		score.setBaseTime(rythm[0]+"/"+rythm[1]);
	}

	private MMLTrack parseMMSPart(String contents) {
		final int intValue[] = { 0, 0 };
		final String stringValue[] = { "", "", "", "" };

		for (String s : contents.split("\n")) {
			TextParser textParser = TextParser.text(s);
			if ( textParser.startsWith("instrument=",
					t -> intValue[0] = convertInstProgram(Integer.parseInt(t)) )) {
			} else if ( textParser.startsWith("panpot=",
					t -> intValue[1] = Integer.parseInt(t) + 64 )) {
			} else if ( textParser.startsWith("name=",    t -> stringValue[0] = t) ) {
			} else if ( textParser.startsWith("ch0_mml=", t -> stringValue[1] = t) ) {
			} else if ( textParser.startsWith("ch1_mml=", t -> stringValue[2] = t) ) {
			} else if ( textParser.startsWith("ch2_mml=", t -> stringValue[3] = t) ) {
				break;
			}
		}

		MMLTrack track = new MMLTrack(stringValue[1], stringValue[2], stringValue[3], "");
		track.setTrackName(stringValue[0]);
		track.setProgram(intValue[0]);
		track.setPanpot(intValue[1]);
		return track;
	}
}
