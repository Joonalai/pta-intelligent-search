
$(document).ready(function() {
	document.getElementById("pta-haku-input-search-text").focus();

	$('#pta-haku-input-container input').keypress(function (e) {
		if (e.which == 13) {
			teeHaku();
		}
	});

	$('#pta-haku-input-container button').click(teeHaku);

	function teeHaku() {
		var hakusanat = $('#pta-haku-input-container input').val();


		var query = {
			hakusanat: hakusanat.split(/\s+/).filter(function(v) { return v.length > 0; })
		};

		var vinkit = $('#pta-tulokset #pta-tulokset-vinkit');
		vinkit.hide();

		var osumat = $('#pta-tulokset #pta-tulokset-osumat');
		osumat.hide();

		var virhe = $('#pta-tulokset #pta-tulokset-virhe');
		virhe.hide();

		$.ajax({
			url: 'hae',
			method: 'POST',
			data: JSON.stringify(query),
			contentType: 'application/json',
			dataType: 'json'
		}).done(function(result) {


			// Vinkit
			var vinkkiLista = $('#pta-tulokset-vinkit-lista', vinkit);
			vinkkiLista.empty();
			result.hakusanavinkit.forEach(function(vinkki) {
				var tmp = $('<div></div>');
				tmp.addClass('pta-tulokset-vinkit-vinkki');
				tmp.text(vinkki);
				vinkkiLista.append(tmp);
			});
			vinkit.show();

			// Tulokset
			var osumaLista = $('#pta-tulokset-osumat-lista', osumat);
			osumaLista.empty();
			result.osumat.forEach(function(osuma) {
				var tmp = $('<div></div>');
				tmp.addClass('pta-tulokset-osumat-osuma');
				
				var title = $('<p></p>');
				title.text(osuma.title + ' ('+Math.round(osuma.relevanssi*100)/100+')');
				title.addClass('pta-tulokset-osumat-osuma-title');
				tmp.append(title);
				
				var desc = $('<div></div>');
				desc.addClass('pta-tulokset-osumat-osuma-desc');
				desc.text(osuma.abstractText);
				desc.hide();
				title.click(function() { desc.toggle(); });
				desc.click(function() { desc.toggle(); });
				
				tmp.append(desc);
				
				var link = $('<a></a>');
				link.text('Avaa paikkatietohakemistossa');
				link.attr('href', osuma.url);
				link.attr('target', '_blank');
				
				tmp.append(link);
				
				//tmp.text('foo: '+JSON.stringify(osuma));
				osumaLista.append(tmp);
			});
			
			osumat.show();
			
		}).fail(function(err) {
			virhe.empty();
			console.error(err);
			var tmp = $('<div></div>');
			tmp.addClass('pta-virhe');
			tmp.text('Tapahtui virhe: '+err.statusText);

			var pre = $('<pre></pre>');
			pre.text(err.responseText);
			tmp.append(pre);

			virhe.append(tmp);
			virhe.show();
		});
	}


	//teeHaku();

});