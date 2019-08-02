package fi.maanmittauslaitos.pta.search;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathException;

import fi.maanmittauslaitos.pta.search.csw.LocalCSWHarvesterSource;
import fi.maanmittauslaitos.pta.search.index.LocalArchiveDocumentSink;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.w3c.dom.Node;

import com.entopix.maui.stemmers.FinnishStemmer;
import com.entopix.maui.stopwords.StopwordsFinnish;

import fi.maanmittauslaitos.pta.search.codelist.InspireThemesImpl;
import fi.maanmittauslaitos.pta.search.codelist.ODFOrganisationNameNormaliserImpl;
import fi.maanmittauslaitos.pta.search.codelist.OrganisationNormaliser;
import fi.maanmittauslaitos.pta.search.codelist.OrganisationNormaliserTextRewriter;
import fi.maanmittauslaitos.pta.search.csw.CSWHarvesterSource;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessingConfiguration;
import fi.maanmittauslaitos.pta.search.documentprocessor.DocumentProcessor;
import fi.maanmittauslaitos.pta.search.documentprocessor.FieldExtractorConfiguration;
import fi.maanmittauslaitos.pta.search.documentprocessor.XPathCustomExtractor;
import fi.maanmittauslaitos.pta.search.documentprocessor.XPathFieldExtractorConfiguration;
import fi.maanmittauslaitos.pta.search.documentprocessor.XPathFieldExtractorConfiguration.FieldExtractorType;
import fi.maanmittauslaitos.pta.search.elasticsearch.PTAElasticSearchMetadataConstants;
import fi.maanmittauslaitos.pta.search.index.DocumentSink;
import fi.maanmittauslaitos.pta.search.index.ElasticsearchDocumentSink;
import fi.maanmittauslaitos.pta.search.metadata.GeographicBoundingBoxXPathCustomExtractor;
import fi.maanmittauslaitos.pta.search.metadata.ISOMetadataExtractorConfigurationFactory;
import fi.maanmittauslaitos.pta.search.metadata.ISOMetadataFields;
import fi.maanmittauslaitos.pta.search.metadata.ResponsiblePartyXPathCustomExtractor;
import fi.maanmittauslaitos.pta.search.text.ExistsInSetProcessor;
import fi.maanmittauslaitos.pta.search.text.MauiTextProcessor;
import fi.maanmittauslaitos.pta.search.text.RDFTerminologyMatcherProcessor;
import fi.maanmittauslaitos.pta.search.text.RegexProcessor;
import fi.maanmittauslaitos.pta.search.text.StopWordsProcessor;
import fi.maanmittauslaitos.pta.search.text.TerminologyExpansionProcessor;
import fi.maanmittauslaitos.pta.search.text.TextProcessingChain;
import fi.maanmittauslaitos.pta.search.text.TextProcessor;
import fi.maanmittauslaitos.pta.search.text.TextSplitterProcessor;
import fi.maanmittauslaitos.pta.search.text.WordCombinationProcessor;
import fi.maanmittauslaitos.pta.search.text.stemmer.StemmerFactory;

public class HarvesterConfig {
	
	private static Logger logger = Logger.getLogger(HarvesterConfig.class);
	
	private static String ENV_CANONICAL_ORGANISATIONS_FILENAME = "CANONICAL_ORGANISATIONS_FILE";
	private static String CANONICAL_ORGANISATIONS_DEFAULT_FILENAME = "canonical_organisations.ods";
	
	public HarvesterSource getCSWSource() {
		HarvesterSource source = new CSWHarvesterSource();
		source.setBatchSize(10);
		source.setOnlineResource("http://paikkatietohakemisto.fi/geonetwork/srv/en/csw");
		//source.setOnlineResource("http://demo.paikkatietohakemisto.fi/geonetwork/srv/en/csw");
		
		return source;
	}

	public HarvesterSource getLocalCSWSource() {
		LocalCSWHarvesterSource source = new LocalCSWHarvesterSource();
		URL cswRoot = this.getClass().getClassLoader().getResource("csws");
		source.setResourceRootURL(cswRoot);
		return source;
	}
	
	
	public DocumentProcessor getCSWRecordProcessor() throws ParserConfigurationException, IOException  {
		ISOMetadataExtractorConfigurationFactory factory = new ISOMetadataExtractorConfigurationFactory();
		
		// Basic configuration
		DocumentProcessingConfiguration configuration = factory.createMetadataDocumentProcessingConfiguration();
		
		
		// Ontology models and and text processors
		Model model = getTerminologyModel();
		RDFTerminologyMatcherProcessor terminologyProcessor = createTerminologyMatcher(model);
		WordCombinationProcessor wordCombinationProcessor = createWordCombinationProcessor(model);
		
		
		// Set up abstract processor (abstract => abstract_uri)
		TextProcessingChain abstractChain = createAbstractProcessingChain(terminologyProcessor, wordCombinationProcessor);
		configuration.getTextProcessingChains().put("abstractProcessor", abstractChain);
		
		FieldExtractorConfiguration abstractUri = configuration.getFieldExtractor(ISOMetadataFields.ABSTRACT).copy();
		abstractUri.setField(PTAElasticSearchMetadataConstants.FIELD_ABSTRACT_URI);
		abstractUri.setTextProcessorName("abstractProcessor");
		
		configuration.getFieldExtractors().add(abstractUri);
		
		// Abstract processor that determines the parents of
		TextProcessingChain abstractParentsChain = createAbstractParentProcessingChain(terminologyProcessor, wordCombinationProcessor, model);
		configuration.getTextProcessingChains().put("abstractParentProcessor", abstractParentsChain);
		
		FieldExtractorConfiguration abstract2Uri = configuration.getFieldExtractor(ISOMetadataFields.ABSTRACT).copy();
		abstract2Uri.setField(PTAElasticSearchMetadataConstants.FIELD_ABSTRACT_URI_PARENTS);
		abstract2Uri.setTextProcessorName("abstractParentProcessor");
		
		configuration.getFieldExtractors().add(abstract2Uri);
		
		
		// Set up maui chain for abstract (abstract => abstract_maui_uri)
		MauiTextProcessor mauiTextProcessor = createMauiProcessingChain(); 
		TextProcessingChain mauiChain = new TextProcessingChain();
		mauiChain.getChain().add(mauiTextProcessor);
		
		configuration.getTextProcessingChains().put("mauiProcessor", mauiChain);
		
		FieldExtractorConfiguration abstractMauiUri = configuration.getFieldExtractor(ISOMetadataFields.ABSTRACT).copy();
		abstractMauiUri.setField(PTAElasticSearchMetadataConstants.FIELD_ABSTRACT_MAUI_URI);
		abstractMauiUri.setTextProcessorName("mauiProcessor");
		
		configuration.getFieldExtractors().add(abstractMauiUri);
		
		// Set up maui chain for abstract (abstract => abstract_maui_uri_parents)
		TextProcessingChain mauiParentsChain = createMauiParentProcessingChain(mauiTextProcessor, model);
		configuration.getTextProcessingChains().put("mauiParentsProcessor", mauiParentsChain);
		
		FieldExtractorConfiguration abstractMauiParentsUri = configuration.getFieldExtractor(ISOMetadataFields.ABSTRACT).copy();
		abstractMauiParentsUri.setField(PTAElasticSearchMetadataConstants.FIELD_ABSTRACT_MAUI_URI_PARENTS);
		abstractMauiParentsUri.setTextProcessorName("mauiParentsProcessor");
		
		configuration.getFieldExtractors().add(abstractMauiParentsUri);
		
		
		// Keyword to uri detection (keywords => keywords_uri) 
		TextProcessingChain keywordChain = createKeywordProcessingChain(terminologyProcessor, wordCombinationProcessor);
		configuration.getTextProcessingChains().put("keywordProcessor", keywordChain);
		
		FieldExtractorConfiguration keywordsUri = configuration.getFieldExtractor(ISOMetadataFields.KEYWORDS_ALL).copy();
		keywordsUri.setField(PTAElasticSearchMetadataConstants.FIELD_KEYWORDS_URI);
		keywordsUri.setTextProcessorName("keywordProcessor");
		
		configuration.getFieldExtractors().add(keywordsUri);
		
		// Extra matchers that are used to match things not matched by pta-intelligent-search-metadata-extractor
		
		// Annotated keywords
		TextProcessingChain isInOntologyFilterProcessor = createIsInOntologyProcessor(terminologyProcessor);
		
		configuration.getTextProcessingChains().put("isInOntologyFilterProcessor", isInOntologyFilterProcessor);
		
		
		XPathFieldExtractorConfiguration annotatedKeywordExtractor = new XPathFieldExtractorConfiguration();
		annotatedKeywordExtractor.setField("annotated_keywords_uri");
		annotatedKeywordExtractor.setType(FieldExtractorType.ALL_MATCHING_VALUES);
		annotatedKeywordExtractor.setXpath("//gmd:descriptiveKeywords/*/gmd:keyword/gmx:Anchor/@xlink:href");
		
		annotatedKeywordExtractor.setTextProcessorName("isInOntologyFilterProcessor");
		
		configuration.getFieldExtractors().add(annotatedKeywordExtractor);
		
		// Copy the title to titleSort (which is a keyword field to allow sorting)
		FieldExtractorConfiguration titleFiSort = configuration.getFieldExtractor(ISOMetadataFields.TITLE).copy();
		titleFiSort.setField("titleFiSort");
		configuration.getFieldExtractors().add(titleFiSort);
		
		FieldExtractorConfiguration titleSvSort = configuration.getFieldExtractor(ISOMetadataFields.TITLE_SV).copy();
		titleSvSort.setField("titleSvSort");
		configuration.getFieldExtractors().add(titleSvSort);
		
		FieldExtractorConfiguration titleEnSort = configuration.getFieldExtractor(ISOMetadataFields.TITLE_EN).copy();
		titleEnSort.setField("titleEnSort");
		configuration.getFieldExtractors().add(titleEnSort);
		
		
		// Extract all organisation names in a text field for full-text search purposes
		TextProcessingChain organisationNameTextProcessor = new TextProcessingChain();
		RegexProcessor whitespaceRemoval = new RegexProcessor();
		whitespaceRemoval.setPattern(Pattern.compile("^\\s*$"));
		whitespaceRemoval.setIncludeMatches(false);
		
		organisationNameTextProcessor.getChain().add(whitespaceRemoval);
		
		configuration.getTextProcessingChains().put("organisationNameTextProcessor", organisationNameTextProcessor);
		
		XPathFieldExtractorConfiguration organisationForSearch = new XPathFieldExtractorConfiguration();
		organisationForSearch.setField("organisationName_text");
		organisationForSearch.setType(FieldExtractorType.ALL_MATCHING_VALUES);
		organisationForSearch.setXpath("//gmd:contact//gmd:organisationName//text()");
		
		organisationForSearch.setTextProcessorName("organisationNameTextProcessor");
		
		configuration.getFieldExtractors().add(organisationForSearch);
		
		// Modify organisation extractor to canonicalize organisation names
		OrganisationNormaliser organisationNormaliser = loadOrganisationNormaliser();
		OrganisationNormaliserTextRewriter orgRewriter = new OrganisationNormaliserTextRewriter();
		orgRewriter.setOrganisationNormaliser(organisationNormaliser);
		
		FieldExtractorConfiguration fec = configuration.getFieldExtractor(ISOMetadataFields.ORGANISATIONS);
		XPathFieldExtractorConfiguration x = (XPathFieldExtractorConfiguration)fec;
		ResponsiblePartyXPathCustomExtractor rpxpce = (ResponsiblePartyXPathCustomExtractor)x.getCustomExtractor();
		rpxpce.setOrganisationNameRewriter(orgRewriter);
		
		
		// Configure INSPIRE theme extractor to normalize the theme to 
		final InspireThemesImpl inspireThemes = new InspireThemesImpl();
		inspireThemes.setCanonicalLanguage("fi"); // Normalize the records to Finnish
		inspireThemes.setModel(loadModels(RDFFormat.RDFXML, "/inspire-theme.rdf.gz"));
		inspireThemes.setHeuristicSearchLanguagePriority("fi", "en", "sv");
		
		FieldExtractorConfiguration inspireFieldExtractorConfiguration = 
				configuration.getFieldExtractor(ISOMetadataFields.KEYWORDS_INSPIRE);
		
		TextProcessingChain inspireThemeNormalizer = new TextProcessingChain();
		inspireThemeNormalizer.getChain().add(new TextProcessor() {
			
			@Override
			public List<String> process(List<String> input) {
				List<String> ret = new ArrayList<>();
				
				for (String str : input) {
					String value = inspireThemes.getCanonicalName(str);
					if (value == null) {
						value = str;
					}
					ret.add(value);
				}

				return ret;
			}
		});
		
		configuration.getTextProcessingChains().put("inspireThemeNormalizer", inspireThemeNormalizer);
		
		inspireFieldExtractorConfiguration.setTextProcessorName("inspireThemeNormalizer");
		
		
		// Extract bounding box area
		
		
		XPathFieldExtractorConfiguration bboxFec = (XPathFieldExtractorConfiguration)
				configuration.getFieldExtractor(ISOMetadataFields.GEOGRAPHIC_BOUNDING_BOX);
		
		XPathFieldExtractorConfiguration bboxAreaFec = (XPathFieldExtractorConfiguration)bboxFec.copy();
		final GeographicBoundingBoxXPathCustomExtractor originalBboxCustomExtractor = (GeographicBoundingBoxXPathCustomExtractor)bboxAreaFec.getCustomExtractor();
		bboxAreaFec.setCustomExtractor(new XPathCustomExtractor() {
			
			@Override
			public Object process(XPath xPath, Node node) throws XPathException {
				Object original = originalBboxCustomExtractor.process(xPath, node);
				@SuppressWarnings("unchecked")
				List<Double> coords = (List<Double>)original;
				if (coords == null) {
					return null;
				} else {
					return (coords.get(2) - coords.get(0)) * (coords.get(3) - coords.get(1));
				}
			}
		});
		bboxAreaFec.setField("geographicBoundingBoxArea");
		configuration.getFieldExtractors().add(bboxAreaFec);
		
		return factory.getDocumentProcessorFactory().createProcessor(configuration);
		
	}


	private OrganisationNormaliser loadOrganisationNormaliser() throws IOException {
		
		try {
			InputStream is;
			String env = System.getProperty(ENV_CANONICAL_ORGANISATIONS_FILENAME);
			if (env != null) {
				logger.info("Loading canonical organisations from file "+env);
				is = new FileInputStream(env);
			} else  {
			
				File file = new File(CANONICAL_ORGANISATIONS_DEFAULT_FILENAME);
				if (file.exists()) {
					is = new FileInputStream(file); 
				} else {
					is = HarvesterConfig.class.getResourceAsStream("/canonical_organisations.ods");
				}
			}
			
			ODFOrganisationNameNormaliserImpl ret = new ODFOrganisationNameNormaliserImpl();
			ret.loadWorkbook(is);
			
			return ret;
		} catch(IOException | ParseException e) {
			throw new IOException("Could not load canonical organisations", e);
		}

	}


	private TextProcessingChain createMauiParentProcessingChain(MauiTextProcessor mauiTextProcessor, Model model) {
		TextProcessingChain ret = new TextProcessingChain();
		
		ret.getChain().add(mauiTextProcessor);

		TerminologyExpansionProcessor expansionProcessor = new TerminologyExpansionProcessor();
		expansionProcessor.setModel(model);
		expansionProcessor.setPredicates(Arrays.asList(SKOS.BROADER));
		
		ret.getChain().add(expansionProcessor);
		
		return ret;
	}


	private TextProcessingChain createIsInOntologyProcessor(RDFTerminologyMatcherProcessor terminologyProcessor) {
		TextProcessingChain isInOntologyFilterProcessor = new TextProcessingChain();
		
		ExistsInSetProcessor allowInOntology = new ExistsInSetProcessor();
		allowInOntology.setAcceptedStrings(terminologyProcessor.getAllKnownTerms());
		isInOntologyFilterProcessor.getChain().add(allowInOntology);
		return isInOntologyFilterProcessor;
	}


	private TextProcessingChain createKeywordProcessingChain(RDFTerminologyMatcherProcessor terminologyProcessor,
			WordCombinationProcessor wordCombinationProcessor) {
		TextProcessingChain keywordChain = new TextProcessingChain();
		RegexProcessor whitespaceRemoval = new RegexProcessor();
		whitespaceRemoval.setPattern(Pattern.compile("^\\s*$"));
		whitespaceRemoval.setIncludeMatches(false);
		
		keywordChain.getChain().add(new TextSplitterProcessor());
		keywordChain.getChain().add(wordCombinationProcessor);
		keywordChain.getChain().add(whitespaceRemoval);
		keywordChain.getChain().add(terminologyProcessor);
		return keywordChain;
	}


	private TextProcessingChain createAbstractProcessingChain(RDFTerminologyMatcherProcessor terminologyProcessor,
			WordCombinationProcessor wordCombinationProcessor) throws IOException {
		TextProcessingChain ret = new TextProcessingChain();
		ret.getChain().add(new TextSplitterProcessor());
		ret.getChain().add(wordCombinationProcessor);
		
		StopWordsProcessor stopWordsProcessor = new StopWordsProcessor();
		stopWordsProcessor.loadWords(HarvesterConfig.class.getResourceAsStream("/nls.fi/pta-intelligent-search/stopwords-fi.txt"));
		ret.getChain().add(stopWordsProcessor);
		ret.getChain().add(terminologyProcessor);
		return ret;
	}
	
	private TextProcessingChain createAbstractParentProcessingChain(RDFTerminologyMatcherProcessor terminologyProcessor,
			WordCombinationProcessor wordCombinationProcessor, Model model) throws IOException {
		TextProcessingChain ret = createAbstractProcessingChain(terminologyProcessor, wordCombinationProcessor);

		TerminologyExpansionProcessor expansionProcessor = new TerminologyExpansionProcessor();
		expansionProcessor.setModel(model);
		expansionProcessor.setPredicates(Arrays.asList(SKOS.BROADER));
		
		ret.getChain().add(expansionProcessor);
		
		return ret;
	}
	

	private MauiTextProcessor createMauiProcessingChain() {
		MauiTextProcessor mauiTextProcessor = new MauiTextProcessor();
		mauiTextProcessor.setMauiStemmer(new FinnishStemmer());
		mauiTextProcessor.setMauiStopWords(new StopwordsFinnish());
		
		mauiTextProcessor.setModelResource("/paikkatietohakemisto-pto.model");
		mauiTextProcessor.setVocabularyName("pto-skos.rdf.gz");
		mauiTextProcessor.setVocabularyFormat("skos");
		mauiTextProcessor.setLanguage("fi");
		
		mauiTextProcessor.init();
		return mauiTextProcessor;
	}


	private RDFTerminologyMatcherProcessor createTerminologyMatcher(Model model) throws IOException {
		RDFTerminologyMatcherProcessor ret = new RDFTerminologyMatcherProcessor();
		ret.setModel(model);
		ret.setTerminologyLabels(Arrays.asList(SKOS.PREF_LABEL, SKOS.ALT_LABEL));
		ret.setStemmer(StemmerFactory.createFinnishStemmer());
		ret.setLanguage("fi");
		return ret;
	}

	private WordCombinationProcessor createWordCombinationProcessor(Model model) throws IOException {
		WordCombinationProcessor ret = new WordCombinationProcessor();
		ret.setModel(model);
		ret.setTerminologyLabels(Arrays.asList(SKOS.PREF_LABEL, SKOS.ALT_LABEL));
		ret.setStemmer(StemmerFactory.createFinnishStemmer());
		ret.setLanguage("fi");
		return ret;
	}
	
	public DocumentSink getDocumentSink() {
		ElasticsearchDocumentSink ret = new ElasticsearchDocumentSink();
		ret.setHostname("localhost");
		ret.setPort(9200);
		ret.setProtocol("http");
		
		ret.setIndex(PTAElasticSearchMetadataConstants.INDEX);
		ret.setType(PTAElasticSearchMetadataConstants.TYPE);
		
		ret.setIdField("@id");
		
		return ret;
	}

	public DocumentSink getLocalDocumentSink(String sinkfile) {
		LocalArchiveDocumentSink localArchiveDocumentSink = new LocalArchiveDocumentSink();
		localArchiveDocumentSink.setOutputFileName(sinkfile);
		return localArchiveDocumentSink;
	}
	

	Model getTerminologyModel() throws IOException {
		return loadModels(RDFFormat.TURTLE, getTerminologyModelResourceName());
	}

	private String getTerminologyModelResourceName() {
		return "/pto-skos.ttl.gz";
	}
	
	private static Model loadModels(RDFFormat format, String...files) throws IOException {
		Model ret = null;
		
		for (String file : files) {
			try (Reader reader = new InputStreamReader(new GZIPInputStream(HarvesterConfig.class.getResourceAsStream(file)))) {
				Model model = Rio.parse(reader, "", format);
				
				if (ret == null) {
					ret = model;
				} else {
					ret.addAll(model);
				}
			}
		}
		
		return ret;
	}
	
}
