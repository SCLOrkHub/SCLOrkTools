SCLOrkEmojiPicker {
	const maxEmojiShown = 100;
	var window;
	var topLabel;
	var emojiView;
	var emojiFont;
	var searchTerm;

	*new { |bounds|
		SCLOrkEmoji.load;
		^super.new.init(bounds);
	}

 	init { |bounds|
		window = Window.new("picker", bounds, false, false);
		emojiFont = Font.new("Noto Color Emoji");
		window.layout = VLayout.new(
			topLabel = StaticText.new,
			emojiView = FlowView.new;
		);
		this.updateSearchTerm(nil);
		window.front;
	}

	updateSearchTerm { |term|
		var matches = IdentitySet.new;
		var path = Array.new;
		searchTerm = term;
		searchTerm.postln;
		emojiView.removeAll;
		emojiView.decorator.reset;
		if (searchTerm.isNil or: { searchTerm.size == 0 }, {
			// Handle empty search term state
			topLabel.string = "% emoji".format(SCLOrkEmoji.map.size);
		}, {
			var pathStart = 0;
			topLabel.string = searchTerm;
			while ({ pathStart < searchTerm.size }, {
				path = path.add(searchTerm[pathStart..pathStart].asSymbol);
				path.postln;
				pathStart = pathStart + 1;
			});
		});

		SCLOrkEmoji.trie.leafDoFrom(path, { |leafPath, leaf|
			if (leafPath.wrapAt(-1) === '_match' and: { matches.size <= maxEmojiShown }, {
				leaf.do({ |item|
					matches.add(item)
				});
			});
		});

		matches.postln;
		emojiView.children.size.postln;

		matches.do({ |item|
			StaticText.new(emojiView)
			.font_(emojiFont)
			.string_(item)
			.toolTip_(SCLOrkEmoji.map.at(item));
		});
	}
}