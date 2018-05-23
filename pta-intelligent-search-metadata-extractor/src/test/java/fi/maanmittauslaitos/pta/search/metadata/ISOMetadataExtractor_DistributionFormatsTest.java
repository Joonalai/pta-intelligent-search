package fi.maanmittauslaitos.pta.search.metadata;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import fi.maanmittauslaitos.pta.search.documentprocessor.Document;

public class ISOMetadataExtractor_DistributionFormatsTest extends BaseMetadataExtractorTest {

	@Test
	public void testMaastotietokantaDistributionFormats() throws Exception {
		Document document = createMaastotietokantaDocument();
		
		List<String> distributionFormats = document.getListValue(ISOMetadataFields.DISTRIBUTION_FORMATS,  String.class);
		assertArrayEquals(new String[] {
				"GML", "Mapinfo MIF/MID", "ESRI Shapefile"
			}, distributionFormats.toArray());
	}

	@Test
	public void testStatFiDistributionFormats() throws Exception {
		Document document = createStatFiWFS();
		
		List<String> distributionFormats = document.getListValue(ISOMetadataFields.DISTRIBUTION_FORMATS,  String.class);
		
		assertArrayEquals(new String[] {
			}, distributionFormats.toArray());
	}

}
