package fi.maanmittauslaitos.pta.search.api;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.maanmittauslaitos.pta.search.api.model.SearchQuery;
import fi.maanmittauslaitos.pta.search.api.model.SearchResult;

@RestController
public class HakuController {
	
	@Autowired
	private HakuKone hakukone;

	@RequestMapping(value = "/v1/search", method = RequestMethod.POST)
	public SearchResult hae(@RequestBody SearchQuery pyynto, @RequestParam("X-CLIENT-LANG") Optional<String> lang) throws IOException
	{
		Language l = sanitizeLanguage(lang.orElse("FI"));
		
		SearchResult tulos = hakukone.haku(pyynto, l);
		
		return tulos;
	}

	private Language sanitizeLanguage(String lang) {
		lang = lang.toUpperCase();
		if (lang.equals("SV")) {
			return Language.SV;
		}
		if (lang.equals("EN")) {
			return Language.EN;
		}
		
		return Language.FI;
	}
	
	
}
