/*
 * Copyright (C) 2014 たんらる
 */

package fourthline.mmlTools;

import static org.junit.Assert.*;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import org.junit.Test;


import fourthline.FileSelect;
import fourthline.mmlTools.core.MMLText;
import fourthline.mmlTools.parser.IMMLFileParser;
import fourthline.mmlTools.parser.MMLParseException;

public class MMLScoreTest extends FileSelect {

	public static void checkMMLScoreWriteToOutputStream(MMLScore score, InputStream inputStream) {
		try {
			int size = inputStream.available();
			byte expectBuf[] = new byte[size];
			inputStream.read(expectBuf);
			inputStream.close();

			// MMLScore -> mmi check
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			score.writeToOutputStream(outputStream);
			String mmiOutput = outputStream.toString("UTF-8");
			assertEquals(new String(expectBuf), mmiOutput);

			// mmi -> re-parse check
			ByteArrayInputStream bis = new ByteArrayInputStream(mmiOutput.getBytes());
			MMLScore reparseScore = new MMLScore().parse(bis);
			assertEquals(mmiOutput, new String(reparseScore.getObjectState()));
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	private void checkMMLFileOutput(MMLScore score, String expectFileName, String expectMML[]) {
		try {
			/* MMLScore.writeToOutputStream() */
			InputStream inputStream = fileSelect(expectFileName);
			checkMMLScoreWriteToOutputStream(score, inputStream);

			/* MMLScore.parse() */
			inputStream = fileSelect(expectFileName);
			MMLScore inputScore = new MMLScore().parse(inputStream).generateAll();
			inputStream.close();
			int i = 0;
			assertEquals(expectMML.length, inputScore.getTrackCount());
			for (MMLTrack track : inputScore.getTrackList()) {
				assertEquals(expectMML[i++], track.getMabiMML());
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testMMLFileFormat0() throws UndefinedTickException {
		MMLScore score = new MMLScore();
		MMLTrack track = new MMLTrack().setMML("MML@aaa,bbb,ccc,dd1;");
		track.setTrackName("track1");
		score.addTrack(track);
		score.getMarkerList().add(new Marker("marker1", 96));

		String mml[] = { "MML@aaa,bbb,ccc,dd1;" };

		checkMMLFileOutput(score.generateAll(), "format0.mmi", mml);
	}

	@Test
	public void testMMLFileFormat1() throws UndefinedTickException {
		MMLScore score = new MMLScore();
		MMLTrack track1 = new MMLTrack().setMML("MML@at150aa1,bbb,ccc,dd1;");
		track1.setTrackName("track1");
		score.addTrack(track1);
		MMLTrack track2 = new MMLTrack().setMML("MML@aaa2,bbt120b,ccc,dd2;");
		track2.setTrackName("track2");
		track2.setProgram(4);
		track2.setSongProgram(120);
		score.addTrack(track2);

		String mml[] = { 
				"MML@at150at120a1,bbb,ccc,dt150dt120&d2.;",
				"MML@at150at120a2,bbb,ccc,dt150dt120&d;"
		};

		checkMMLFileOutput(score.generateAll(), "format1.mmi", mml);
	}

	@Test
	public void testMMLFileFormat_r0() throws UndefinedTickException {
		MMLScore score = new MMLScore();
		MMLTrack track = new MMLTrack().setMML("MML@r1t180c8;");
		track.setTrackName("track1");
		score.addTrack(track);

		String mml[] = { "MML@v0c1t180v8c8,,;" };

		checkMMLFileOutput(score.generateAll(), "format_r0.mmi", mml);
	}

	@Test
	public void testMMLFileFormat_r1() throws UndefinedTickException {
		MMLScore score = new MMLScore();
		MMLTrack track1 = new MMLTrack().setMML("MML@r1>f+1t120&f+1;");
		track1.setTrackName("track1");
		score.addTrack(track1);

		MMLTrack track2 = new MMLTrack().setMML("MML@r1r1a+1;");
		track2.setTrackName("track2");
		score.addTrack(track2);

		MMLTrack track3 = new MMLTrack().setMML("MML@d1;");
		track3.setTrackName("track3");
		score.addTrack(track3);

		String mml[] = {
				"MML@l1r>f+t120&f+,,;",
				"MML@l1v0cct120v8a+,,;",
				"MML@l1dv0ct120,,;"
		};

		checkMMLFileOutput(score.generateAll(), "format_r1.mmi", mml);
	}

	/**
	 * ファイルをparseして, 出力文字が増加していないか確認する.
	 * @param filename
	 */
	private void mmlFileParse(String filename) {
		File file = new File(filename);
		if (file.exists()) {
			IMMLFileParser fileParser = IMMLFileParser.getParser(file);
			try {
				MMLScore score = fileParser.parse(new FileInputStream(file));
				System.out.println(filename);
				score.getTrackList().forEach(t -> {
					try {
						String mml1 = t.getOriginalMML();
						String rank1 = t.mmlRankFormat();
						t.generate();
						String mml2 = t.getOriginalMML();
						String rank2 = new MMLText().setMMLText(mml2).mmlRankFormat();
						String rank3 = t.mmlRankFormat();
						System.out.println(rank1 + " -> " + rank2 + ", " + rank3);
						assertTrue(mml1.length() >= mml2.length());
					} catch (Exception e) {}
				});
			} catch (MMLParseException | FileNotFoundException e) {}
		}
	}

	/**
	 * ローカルのファイルを読み取って, MML最適化に劣化がないかどうかを確認するテスト.
	 */
	@Test
	public void testLocalMMLParse() {
		try {
			String listFile = "localMMLFileList.txt";
			InputStream stream = fileSelect(listFile);
			if (stream == null) {
				fail("not found "+listFile);
				return;
			}
			InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
			new BufferedReader(reader).lines().forEach(s -> {
				System.out.println(s);
				mmlFileParse(s);
			});
		} catch (IOException e) {}
	}
}
