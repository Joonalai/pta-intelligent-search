package fi.maanmittauslaitos.pta.search.csw;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import fi.maanmittauslaitos.pta.search.Document;
import fi.maanmittauslaitos.pta.search.HarvesterSource;
import fi.maanmittauslaitos.pta.search.HarvestingException;
import fi.maanmittauslaitos.pta.search.xpath.FieldExtractorConfiguration;
import fi.maanmittauslaitos.pta.search.xpath.FieldExtractorConfiguration.FieldExtractorType;
import fi.maanmittauslaitos.pta.search.xpath.XPathExtractionConfiguration;
import fi.maanmittauslaitos.pta.search.xpath.XPathProcessor;
import fi.maanmittauslaitos.pta.search.xpath.XPathProcessorFactory;

public class CSWHarvesterSource extends HarvesterSource {
	private static Logger logger = Logger.getLogger(CSWHarvesterSource.class);

	@Override
	public Iterator<InputStream> iterator() {
		return new CSWIterator();
	}

	private class CSWIterator implements Iterator<InputStream> {
		private int numberOfRecordsProcessed = 0;
		private int numberOfRecordsInService;
		private LinkedList<String> idsInBatch = null;
		private boolean failed = false;

		public CSWIterator() {
			idsInBatch = new LinkedList<>();
			getNextBatch();
		}
		

		@Override
		public boolean hasNext() {
			if (failed) {
				return false;
			}
			return numberOfRecordsProcessed < numberOfRecordsInService;
		}

		@Override
		public InputStream next() {
			if (idsInBatch.size() == 0) {
				getNextBatch();
			}

			if (idsInBatch.size() == 0) {
				return null;
			}

			String id = idsInBatch.removeFirst();
			numberOfRecordsProcessed++;

			return readRecord(id);
		}

		private InputStream readRecord(String id) {
			logger.debug("Requesting record with id" + id);

			StringBuffer reqUrl = new StringBuffer(getOnlineResource());
			if (reqUrl.indexOf("?") == -1) {
				reqUrl.append("?");
			} else if (reqUrl.charAt(reqUrl.length()-1) != '&') {
				reqUrl.append("&");
			}
			
			reqUrl.append("SERVICE=CSW&REQUEST=GetRecordById&VERSION=2.0.2&outputSchema=http://www.isotc211.org/2005/gmd&elementSetName=full");
			
			try {
				reqUrl.append("&id="+URLEncoder.encode(id, "UTF-8"));
				
				logger.trace("CSW GetRecordById URL: "+reqUrl);
				
				URL url = new URL(reqUrl.toString());
				return url.openStream();
				
			} catch (IOException e) {
				throw new HarvestingException(e);
			}
		}

		
		private void getNextBatch() {
			int startPosition = 1 + numberOfRecordsProcessed;
			int maxRecords = getBatchSize();
			logger.debug("Requesting records startPosition = " + startPosition + ",maxRecords = " + maxRecords);
			
			try {
				
				StringBuffer reqUrl = new StringBuffer(getOnlineResource());
				if (reqUrl.indexOf("?") == -1) {
					reqUrl.append("?");
				} else if (reqUrl.charAt(reqUrl.length()-1) != '&') {
					reqUrl.append("&");
				}
				
				reqUrl.append("SERVICE=CSW&REQUEST=GetRecords&VERSION=2.0.2&typeNames=gmd%3AMD_Metadata&resultType=results&elementSetName=brief");
				
				reqUrl.append("&startPosition="+startPosition+"&maxRecords="+maxRecords);
				
				logger.trace("CSW GetRecords URL: "+reqUrl);
				
				URL url = new URL(reqUrl.toString());
				
				try (InputStream is = url.openStream()) {
					XPathExtractionConfiguration configuration = new XPathExtractionConfiguration();
					configuration.getNamespaces().put("dc", "http://purl.org/dc/elements/1.1/");
					configuration.getNamespaces().put("csw", "http://www.opengis.net/cat/csw/2.0.2");

					FieldExtractorConfiguration numberOfRecordsMatched = new FieldExtractorConfiguration();
					numberOfRecordsMatched.setField("numberOfRecordsMatched");
					numberOfRecordsMatched.setType(FieldExtractorType.FIRST_MATCHING_VALUE);
					numberOfRecordsMatched.setXpath("//csw:SearchResults/@numberOfRecordsMatched");
					configuration.getFieldExtractors().add(numberOfRecordsMatched);

					FieldExtractorConfiguration ids = new FieldExtractorConfiguration();
					ids.setField("ids");
					ids.setType(FieldExtractorType.ALL_MATCHING_VALUES);
					ids.setXpath("//dc:identifier/text()");
					configuration.getFieldExtractors().add(ids);

					XPathProcessorFactory xppf = new XPathProcessorFactory();
					XPathProcessor processor = xppf.createProcessor(configuration);
					Document doc = processor.processDocument(is);

					logger.debug("\tReceived ids: " + doc.getFields().get("ids"));
					logger.debug("\tnumberOfRecordsMatched = " + doc.getFields().get("numberOfRecordsMatched"));

					idsInBatch.addAll(doc.getFields().get("ids"));
					numberOfRecordsInService = Integer.parseInt(doc.getFields().get("numberOfRecordsMatched").get(0));
				}
				
				
			} catch(IOException | ParserConfigurationException | SAXException | XPathException e) {
				throw new HarvestingException(e);
			}
		}
	}
}
