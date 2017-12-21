package fi.maanmittauslaitos.pta.search.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpHost;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fi.maanmittauslaitos.pta.search.text.RDFTerminologyMatcherProcessor;
import fi.maanmittauslaitos.pta.search.text.stemmer.Stemmer;
import fi.maanmittauslaitos.pta.search.text.stemmer.StemmerFactor;

@Configuration
public class ApplicationConfiguration {

	@Bean
	public RDFTerminologyMatcherProcessor terminologyMatcher(Model terminologyModel, Stemmer stemmer) throws IOException {
		RDFTerminologyMatcherProcessor terminologyProcessor = new RDFTerminologyMatcherProcessor();
		terminologyProcessor.setModel(terminologyModel);
		terminologyProcessor.setTerminologyLabels(Arrays.asList(SKOS.PREF_LABEL, SKOS.ALT_LABEL)); // TODO: lisää SKOS.EXACT_MATCH ?
		terminologyProcessor.setStemmer(stemmer);
		return terminologyProcessor;
	}
	
	@Bean
	public Stemmer stemmer() {
		return StemmerFactor.createStemmer();
	}
	
	@Bean
	public RestHighLevelClient elasticsearchClient() throws UnknownHostException {
		RestHighLevelClient client = new RestHighLevelClient(
		        RestClient.builder(
		                new HttpHost("localhost", 9200, "http")));
		
		return client;
	}
	
	@Bean
	public Model terminologyModel() throws IOException {
		//return loadModels("/koko-skos.ttl.gz");
		return loadModels("/ysa-skos.ttl.gz");
	}

	public static Model loadModels(String...files) throws IOException {
		Model ret = null;
		
		for (String file : files) {
			try (Reader reader = new InputStreamReader(new GZIPInputStream(ApplicationConfiguration.class.getResourceAsStream(file)))) {
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
	
	@Bean
	public HintProvider hintProvider(Model terminologyModel, Stemmer stemmer) {
		NodeColorizationHintProviderImpl ret = new NodeColorizationHintProviderImpl();
		ret.setMaxColorizationDepth(2);
		ret.setStemmer(stemmer);
		ret.setModel(terminologyModel);
		
		List<Entry<IRI, Double>> weights = new ArrayList<>();
		
		weights.add(new AbstractMap.SimpleEntry<>(SKOS.BROADER, 0.25));
		weights.add(new AbstractMap.SimpleEntry<>(SKOS.RELATED, 0.15));
		
		ret.setRelationsAndWeights(weights);
		
		return ret;
	}

	
	@Bean
	public HakuKone hakuKone(RDFTerminologyMatcherProcessor textProcessor, RestHighLevelClient elasticsearchClient, Model model, HintProvider hintProvider) throws IOException {
		ElasticsearchHakuKoneImpl ret = new ElasticsearchHakuKoneImpl();
		ret.setClient(elasticsearchClient);
		
		OntologyElasticsearchQueryProviderImpl queryProvider = new OntologyElasticsearchQueryProviderImpl();
		queryProvider.addRelationPredicate(SKOS.NARROWER);
		queryProvider.setTextProcessor(textProcessor);
		queryProvider.setModel(model);
		queryProvider.setOntologyLevels(2);
		queryProvider.setWeightFactor(0.5);
		
		ret.setQueryProvider(queryProvider);
		ret.setHintProvider(hintProvider);
		
		return ret;
	}
}
