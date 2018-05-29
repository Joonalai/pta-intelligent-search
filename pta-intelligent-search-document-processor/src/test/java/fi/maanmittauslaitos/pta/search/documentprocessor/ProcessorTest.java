package fi.maanmittauslaitos.pta.search.documentprocessor;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpression;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import fi.maanmittauslaitos.pta.search.documentprocessor.Document;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessingConfiguration;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessor;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessorFactory;
import fi.maanmittauslaitos.pta.search.documentprocessor.XPathFieldExtractorConfiguration;
import fi.maanmittauslaitos.pta.search.documentprocessor.XPathFieldExtractorConfiguration.FieldExtractorType;
import fi.maanmittauslaitos.pta.search.text.TextProcessingChain;
import fi.maanmittauslaitos.pta.search.text.TextProcessor;

public class ProcessorTest {
	private DocumentProcessingConfiguration configuration;
	
	@Before
	public void setUp() throws Exception {
		configuration = new DocumentProcessingConfiguration();
		configuration.getNamespaces().put("gmd", "http://www.isotc211.org/2005/gmd");
		configuration.getNamespaces().put("gco", "http://www.isotc211.org/2005/gco");
		
		XPathFieldExtractorConfiguration idExtractor = new XPathFieldExtractorConfiguration();
		idExtractor.setField("@id");
		idExtractor.setType(FieldExtractorType.FIRST_MATCHING_VALUE);
		idExtractor.setXpath("//gmd:fileIdentifier/*/text()");
		
		configuration.getFieldExtractors().add(idExtractor);
	}

	@Test
	public void testSingleValueExtraction() throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(
			("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\">\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>this-is-my-id</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"</gmd:MD_Metadata>\n").getBytes("UTF-8"));
		
		DocumentProcessor processor = new DocumentProcessorFactory().createProcessor(configuration);
		
		Document doc = processor.processDocument(bais);
		
		List<String> ids = doc.getListValue("@id", String.class);
		assertNotNull(ids);
		assertEquals(1, ids.size());
		String id = ids.get(0);
		assertEquals("this-is-my-id", id);
	}
	
	@Test
	public void testSingleValueExtractionWith1to1Processor() throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(
			("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\">\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>this-is-my-id</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"</gmd:MD_Metadata>\n").getBytes("UTF-8"));
		
		TextProcessingChain oneToOne = new TextProcessingChain();
		
		oneToOne.getChain().add(new TextProcessor() {
			
			@Override
			public List<String> process(List<String> input) {
				List<String> ret = new ArrayList<>();
				for (String str : input) {
					ret.add(str+"-1");
				}
				return ret;
			}
		});
		
		((XPathFieldExtractorConfiguration)configuration.getFieldExtractors().get(0)).setTextProcessorName("1to1");
		configuration.getTextProcessingChains().put("1to1", oneToOne);
		
		DocumentProcessor processor = new DocumentProcessorFactory().createProcessor(configuration);
		
		Document doc = processor.processDocument(bais);
		
		List<String> ids = doc.getListValue("@id", String.class);
		assertNotNull(ids);
		assertEquals(1, ids.size());
		String id = ids.get(0);
		assertEquals("this-is-my-id-1", id);
	}


	@Test
	public void testSingleValueExtractionMultipleInDocument() throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(
			("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\">\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>this-is-my-id</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>this-is-not-the-id</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"</gmd:MD_Metadata>\n").getBytes("UTF-8"));
		
		DocumentProcessor processor = new DocumentProcessorFactory().createProcessor(configuration);
		
		Document doc = processor.processDocument(bais);
		
		List<String> ids = doc.getListValue("@id", String.class);
		assertNotNull(ids);
		assertEquals(1, ids.size());
		String id = ids.get(0);
		assertEquals("this-is-my-id", id);
	}
	

	@Test
	public void testMultiValueExtractionMultipleInDocument() throws Exception {
		
		XPathFieldExtractorConfiguration extraExtractor = new XPathFieldExtractorConfiguration();
		extraExtractor.setField("extra");
		extraExtractor.setType(FieldExtractorType.ALL_MATCHING_VALUES);
		extraExtractor.setXpath("//gmd:fileIdentifier/*/text()");
		
		
		configuration.getFieldExtractors().add(extraExtractor);
		
		
		ByteArrayInputStream bais = new ByteArrayInputStream(
			("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\">\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>this-is-my-id</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>another-ONE</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"</gmd:MD_Metadata>\n").getBytes("UTF-8"));
		
		DocumentProcessor processor = new DocumentProcessorFactory().createProcessor(configuration);
		
		Document doc = processor.processDocument(bais);
		
		List<String> ids = doc.getListValue("extra", String.class);
		assertNotNull(ids);
		assertEquals(2, ids.size());
		String id = ids.get(0);
		assertEquals("this-is-my-id", id);
		
		String id2 = ids.get(1);
		assertEquals("another-ONE", id2);
	}

	
	@Test
	public void testCustomExtractor() throws Exception {
		ByteArrayInputStream bais = new ByteArrayInputStream(
			("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<gmd:MD_Metadata xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\">\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>one</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"  <gmd:fileIdentifier>\n" +
			"      <gco:CharacterString>two</gco:CharacterString>\n" +
			"  </gmd:fileIdentifier>\n" +
			"</gmd:MD_Metadata>\n").getBytes("UTF-8"));
		

		XPathFieldExtractorConfiguration customExtractor = new XPathFieldExtractorConfiguration();
		customExtractor.setField("foo");
		customExtractor.setType(FieldExtractorType.CUSTOM_CLASS);
		customExtractor.setCustomExtractor(new XPathCustomExtractor() {
			@Override
			public Object process(XPath xPath, Node node) throws XPathException {
				XPathExpression expr = xPath.compile("./gco:CharacterString/text()");
				
				NodeList tmp = (NodeList) expr.evaluate(node, XPathConstants.NODESET);
				return tmp.item(0).getNodeValue();
			}
		});
		customExtractor.setXpath("/gmd:MD_Metadata/gmd:fileIdentifier");
		
		configuration.getFieldExtractors().add(customExtractor);
		
		DocumentProcessor processor = new DocumentProcessorFactory().createProcessor(configuration);
		
		Document doc = processor.processDocument(bais);
		
		List<String> values = doc.getListValue("foo", String.class);
		
		assertEquals(2, values.size());
		String val1 = values.get(0);
		assertEquals("one", val1);
		String val2 = values.get(1);
		assertEquals("two", val2);
		
	}

}
