SCLOrkEmojiMenu : Menu {
	var trieNode;
	var searchString;
	var isPopulated;

	// For the root menu we want to prepopulate it, so that the windowing system knows
	// how tall it is and will better fit it on screen.
	*new { |trieNode, searchString, prepopulate = false|
		var f = { |menu, what|
			if (what === \aboutToShow, { menu.prPopulate });
		};
		var menu = super.new.init.prInit(trieNode, searchString, prepopulate);

		menu.addDependant(f);
		menu.onClose_({ menu.removeDependant(f) });
		^menu;
	}

	prInit { |inTrieNode, inSearchString, prepopulate = false|
		trieNode = inTrieNode;
		searchString = inSearchString;
		this.string = searchString + "(%)".format(trieNode.at(\_count));
		isPopulated = false;
		if (prepopulate, { this.prPopulate });
	}

	prPopulate {
		if (isPopulated.not, {
			var match = trieNode.at(\_match);
			isPopulated = true;

			// If any matches we first add them.
			if (match.notNil, {
				var matchList = Array.new(match.size);
				var targetMenu = this;

				match.do({ |item, index|
					matchList = matchList.add(SCLOrkEmoji.map.at(item) ++ ":" + item);
				});
				matchList.sort;
				matchList.size.do({ |i|
					if (i % 25 == 0 and: { i > 0 }, {
						var newMenu = Menu.new.string_(
							"more.. (%)".format(matchList.size - i));
						targetMenu.addAction(newMenu);
						targetMenu = newMenu;
					});
					targetMenu.addAction(MenuAction.new(matchList[i], {}));
				});

				this.addAction(MenuAction.separator);
			});

			trieNode.sortedKeysValuesDo({ |key, value|
				if (key.asString[0] !== $_, {
					this.addAction(SCLOrkEmojiMenu.new(value, searchString ++ key.asString));
				});
			});
		});
	}
}
