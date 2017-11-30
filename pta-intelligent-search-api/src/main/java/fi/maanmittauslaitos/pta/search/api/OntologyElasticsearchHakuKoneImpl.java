package fi.maanmittauslaitos.pta.search.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;


public class OntologyElasticsearchHakuKoneImpl extends AbstractElasticsearchHakuKoneImpl {

	private int ontologyLevels = 1;
	private double weightFactor = 0.5; // weightForLevel(1) = 1.0, weightForLevel(x) = weightForLevel(x-1) * weightFactor  
	
	public void setOntologyLevels(int ontologyLevels) {
		this.ontologyLevels = ontologyLevels;
	}
	
	public int getOntologyLevels() {
		return ontologyLevels;
	}
	
	@Override
	protected SearchSourceBuilder buildSearchSource(Set<SearchTerm> termit) {
		SearchSourceBuilder sourceBuilder = new SearchSourceBuilder(); 
		
		BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
		
		List<MatchQueryBuilder> queries = new ArrayList<>();
		for (SearchTerm term : termit) {
			MatchQueryBuilder tmp = QueryBuilders.matchQuery("sisalto", term.resource);
			tmp.operator(Operator.OR);
			tmp.fuzziness(Fuzziness.ZERO);
			queries.add(tmp);
		}
		
		// Käytetään hakutermejä sekä filtteröimään, että pisteyttämään
		//boolQuery.filter().addAll(queries);
		boolQuery.must().addAll(queries); // Pisteytystä pitää parantaa, koska ontologiapuusta löytyvät alitermit tulee olla pienemmällä painoarvolla
		sourceBuilder.query(boolQuery);
		
		
		
		return sourceBuilder;
	}

	@Override
	protected Set<SearchTerm> getSearchTerms(HakuPyynto pyynto) {
		Set<SearchTerm> termit = new HashSet<>();
		
		
		for (String hakusana : pyynto.getHakusanat()) {
			for (String termi : getTextProcessor().process(hakusana)) {
				termit.add(new SearchTerm(termi, 1.0));
			}
		}
		
		return termit;
	}
	
}
