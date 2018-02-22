package fi.maanmittauslaitos.pta.search.api.hints;

import java.util.List;

import fi.maanmittauslaitos.pta.search.api.HakuTulos.Hit;

public interface HintProvider {
	public List<String> getHints(List<String> pyyntoTerms, List<Hit> hits);
}
