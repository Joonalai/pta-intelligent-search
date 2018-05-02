package fi.maanmittauslaitos.pta.search.metadata;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Before;

import fi.maanmittauslaitos.pta.search.documentprocessor.Document;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessingException;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessor;

public abstract class BaseMetadataExtractorTest {

	protected DocumentProcessor processor;
	
	@Before
	public void setUp() throws Exception {
		processor = new ISOMetadataExtractorConfigurationFactory().createMetadataDocumentProcessor();
	}

	protected Document createMaastotietokantaDocument()
			throws DocumentProcessingException, IOException, FileNotFoundException {
		Document document;
		try (FileInputStream fis = new FileInputStream("src/test/resources/ddad3347-05ca-401a-b746-d883d4110180.xml")) {
			document = processor.processDocument(fis);
		}
		return document;
	}

}
