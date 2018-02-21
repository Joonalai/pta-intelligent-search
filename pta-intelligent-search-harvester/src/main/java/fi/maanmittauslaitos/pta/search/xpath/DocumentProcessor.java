package fi.maanmittauslaitos.pta.search.xpath;

import java.io.InputStream;


import fi.maanmittauslaitos.pta.search.Document;

public interface DocumentProcessor {
	public Document processDocument(InputStream is) throws DocumentProcessingException;
}
