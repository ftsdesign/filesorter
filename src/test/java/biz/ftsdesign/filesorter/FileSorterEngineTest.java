package biz.ftsdesign.filesorter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FileSorterEngineTest {

	@Test
	void testIsGoodName() {
		assertTrue(FileSorterEngine.isGoodName("a8b8b1da-62d6-4699-989d-6d6995885c51.txt"));
		assertTrue(FileSorterEngine.isGoodName("a8b8b1da-62d6-4699-989d-6d6995885c51.TXT"));
		assertFalse(FileSorterEngine.isGoodName("a8b8b1da-62d6-4699-989d-6d6995885c51."));
		assertFalse(FileSorterEngine.isGoodName("a8b8b1da-62d6-4699-989d-6d6995885c51111.txt"));
	}

}
