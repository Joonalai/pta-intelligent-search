package fi.maanmittauslaitos.pta.search;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import com.fasterxml.jackson.databind.ObjectMapper;

import fi.maanmittauslaitos.pta.search.text.RDFTerminologyMatcherProcessor;
import fi.maanmittauslaitos.pta.search.text.StopWordsProcessor;
import fi.maanmittauslaitos.pta.search.text.TextProcessingChain;
import fi.maanmittauslaitos.pta.search.text.TextSplitterProcessor;
import fi.maanmittauslaitos.pta.search.text.stemmer.FinnishShowballStemmerImpl;
import fi.maanmittauslaitos.pta.search.xpath.FieldExtractorConfiguration;
import fi.maanmittauslaitos.pta.search.xpath.XPathExtractionConfiguration;
import fi.maanmittauslaitos.pta.search.xpath.XPathProcessor;
import fi.maanmittauslaitos.pta.search.xpath.XPathProcessorFactory;
import fi.maanmittauslaitos.pta.search.xpath.FieldExtractorConfiguration.FieldExtractorType;

public class ProcessorTest {

	private static Model loadModels(String...files) throws IOException {
		Model ret = null;
		
		for (String file : files) {
			try (FileReader reader = new FileReader(file)) {
				Model model = Rio.parse(reader, "", RDFFormat.TURTLE);
				
				if (ret == null) {
					ret = model;
				} else {
					ret.addAll(model);
				}
			}
		}
		
		return ret;
	}
	
	public static void main(String[] args) throws Exception {
		XPathExtractionConfiguration configuration = new XPathExtractionConfiguration();
		configuration.getNamespaces().put("gmd", "http://www.isotc211.org/2005/gmd");
		configuration.getNamespaces().put("gco", "http://www.isotc211.org/2005/gco");
		
		TextProcessingChain abstractChain = new TextProcessingChain();
		abstractChain.getChain().add(new TextSplitterProcessor());
		
		StopWordsProcessor stopWordsProcessor = new StopWordsProcessor();
		stopWordsProcessor.setStopwords(Arrays.asList("ja", "tai", "on", "jonka", "mitä", "koska")); // TODO .. tämä ei riitä, ehei
		abstractChain.getChain().add(stopWordsProcessor);
		
		RDFTerminologyMatcherProcessor terminologyProcessor = new RDFTerminologyMatcherProcessor();
		terminologyProcessor.setModel(loadModels("src/test/resources/ysa-skos.ttl"));
		terminologyProcessor.setTerminologyLabels(Arrays.asList(SKOS.PREF_LABEL, SKOS.ALT_LABEL));
		terminologyProcessor.setStemmer(new FinnishShowballStemmerImpl());
		
		abstractChain.getChain().add(terminologyProcessor);
		
		configuration.getTextProcessingChains().put("abstractProcessor", abstractChain);
		
		{
			FieldExtractorConfiguration idExtractor = new FieldExtractorConfiguration();
			idExtractor.setField("@id");
			idExtractor.setType(FieldExtractorType.FIRST_MATCHING_VALUE);
			idExtractor.setXpath("//gmd:fileIdentifier/*/text()");
			
			configuration.getFieldExtractors().add(idExtractor);
		}
		
		{
			FieldExtractorConfiguration keywordExtractor = new FieldExtractorConfiguration();
			keywordExtractor.setField("avainsanat");
			keywordExtractor.setType(FieldExtractorType.ALL_MATCHING_VALUES);
			keywordExtractor.setXpath("//gmd:MD_Keywords/gmd:keyword/*/text()");
			
			//keywordExtractor.setTextProcessorName("abstractProcessor");
			
			configuration.getFieldExtractors().add(keywordExtractor);
		}
		
		{
			FieldExtractorConfiguration abstractExtractor = new FieldExtractorConfiguration();
			abstractExtractor.setField("sisalto");
			abstractExtractor.setType(FieldExtractorType.ALL_MATCHING_VALUES);
			abstractExtractor.setXpath("//gmd:abstract/*/text()");
			
			abstractExtractor.setTextProcessorName("abstractProcessor");
			
			configuration.getFieldExtractors().add(abstractExtractor);
		}
		
		
		XPathProcessor processor = new XPathProcessorFactory().createProcessor(configuration);
		
		Document document;
		try (FileInputStream fis = new FileInputStream("src/test/resources/metadata/1719dcdd-0f24-4406-a347-354532c97bde.xml")) {
			document = processor.processDocument(fis);
		}
		
		System.out.println(document.getFields().get("avainsanat"));
		System.out.println(document.getFields().get("sisalto"));
		ObjectMapper objectMapper = new ObjectMapper();
		
		objectMapper.writeValue(System.out, document.getFields());
	}

}
