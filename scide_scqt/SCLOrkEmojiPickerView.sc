SCLOrkEmojiPickerView : TextView {
	var sclorkChat;
	var searchPath;
	var <searchString;

	*new { |fontSize, sclorkChat, maxHeight|
		// Load the Emoji trie and map.
		SCLOrkEmoji.load;

		^super.new.init(fontSize, sclorkChat, maxHeight);
	}

	init { |fontSize, sclorkChat, maxHeight|
		sclorkChat = sclorkChat;

		this.editable = false;
		this.font = Font(Font.defaultSansFace, fontSize);

		this.clearSearch;
		this.hidePicker;
	}

	showPicker {
		this.visible = true;
		this.updateResults;
	}

	hidePicker {
		this.visible = false;
	}

	clearSearch {
		searchString = "";
		searchPath = [ ];
	}

	appendSearch { |char|
		searchString = searchString ++ char;
		searchPath = searchPath.add(char.asSymbol);
		this.string = searchPath;
		this.updateResults;
	}

	deleteLast {
		searchString.pop;
		searchPath.pop;
		this.updateResults;
	}

	updateResults {
		if (this.visible, {
			var node = SCLOrkEmoji.trie.at(*searchPath);
			if (node.isNil, {
				this.string = searchString + "<not found>";
			}, {
				if (node.class === IdentityDictionary, {
					// Add count to overall match string
					var results = "% (%)\n".format(searchString, node.at(\count));
					var match = node.at(\match);
					if (match.notNil, {
						match.do({ |item|
							results = results ++ " " ++ item;
						});
					});

					node.sortedKeysValuesDo({ |key, value|
						if (key != \count and: { key != \match }, {
							results = results ++ " " ++ key.asString;
						});
					});

					this.string = results;
				}, {
					"node class: %, search path: %".format(node.class, searchPath).postln;
				});
			});
		});
	}
}
