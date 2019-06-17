(
// The input file describes three things per emoji:
//   a) a GUID-style hexadecimal ID
//   b) the code point (so the actual emoji characters)
//   c) a unique descriptive phrase
//
// This script parses the input file to build a couple of data structures with the goal
// of supporting an efficient interactive search for emoji by descriptive phrase. A few
// example inputs to the system are:
// 1F44B, 👋 "waving hand"
// 1F44B 1F3FB, 👋🏻 "waving hand: light skin tone"
// 1F44B 1F3FC, 👋🏼 "waving hand: medium-light skin tone"
// 1F44B 1F3FD, 👋🏽 "waving hand: medium skin tone"
// 1F44B 1F3FE, 👋🏾 "waving hand: medium-dark skin tone"
// 1F44B 1F3FF, 👋🏿 "waving hand: dark skin tone"
//
// Building a trie to search for individual words in the corpus results in wildly uneven
// results, for example the word "hand" has 210 emoji with that word in their description,
// "woman" has 477, and "skin" has 1295. So while search by individual keyword is useful,
// we also need an ability to *filter* by one or more keywords, to get the results down to
// a tractable number (say 10 or so).

// On the converse the word "ascension" appears exactly once:
// 1F1E6 1F1E8, 🇦🇨 "flag: Ascension Island"
// And is in fact uniquely identifiable from the prefix "asc", so the tree needs "pruning"
// to remove the intermediate nodes from "asc" to the match at "ascencion", increasing
// the usability of the search as it can skip to the uniquely identified symbol (or set
// of symbols) right away. Another good test case is "orange" and "orangutan", which
// share some common redundant nodes.

// Trie structure.
// This script builds one MultiLevelIdentityDictionary. Keys are generally sucessive
// alphanumeric characters in a search path, but may occur more than one at a time due
// to pruning, so for example the search path for ascension might be (\a, \s, \cension).
// Non-search-path keys are identified with an underscore prefix, and may or may not be
// present at any node within the tree. There are currently two in use:
// \_match - a literal array of matching unicode characters that contain the word at this
//   search path within their description.
// \_count - a count of the number of matching unicode characters below this node in the
//   Trie.

// Add ids for stuff that doesn't render or doesn't work for SCLOrk here, it won't be added
// to the trie. Can copy directly from the comment in the SCLOrkEmoji.sc file if seen not
// rendering correctly there, then re-run the script. Please try and keep in alphabetical
// order by description.
var excludeIds = IdentitySet.newFrom([
]);
var docLines = File.readAllString(
	"~/src/SCLOrkTools/scripts/emoji-test.txt".standardizePath).split($\n);
var uniTrie = MultiLevelIdentityDictionary.new;
var finalTrie = MultiLevelIdentityDictionary.new;
var descMap = IdentityDictionary.new;
var idMap = IdentityDictionary.new;
var outFile;

// Construct a Trie at basePath from the provided set of unicode characters.
var buildTrie = { |basePath, includeChars|
	// add base count.
	uniTrie.put(*(basePath ++ [\_count, 0]));

	includeChars.do({ |uniChar|
		var description = descMap.at(uniChar);
		description.split($ ).do({ |word|
			var path = Array.new(word.size);
			word.size.do({ |i|
				// Limit search terms to alphanumeric only.
				if (word[i].isAlphaNum, {
					path = path.add(word[i].asSymbol);
				});
			});
			// Add to match leaf array if not at root.
			if (path.size > 0, {
				var match = uniTrie.at(*(basePath ++ path ++ [\_match]));
				if (match.isNil, {
					match = IdentitySet.new;
					uniTrie.put(*(basePath ++ path ++ [\_match, match]));
				});
				if (match.includes(uniChar).not, {
					match = match.add(uniChar);
					// Traverse path back up to root, incrementing count each step.
					while ({ path.size > 0 }, {
						var count = uniTrie.at(*(basePath ++ path ++ [\_count]));
						if (count.notNil, {
							count = count + 1;
						}, {
							count = 1;
						});
						uniTrie.put(*(basePath ++ path ++ [\_count, count]));
						path = path[..path.size - 2];
					});
					uniTrie.put(*(basePath ++
						[\_count, uniTrie.at(*(basePath ++ [\_count])) + 1]));
				});
			});
		});
	});
};

// Flatten redundant nodes.
var pruneTrie = { |uniNode, uniPath, prunePath|
	// If this node has only one alphanumeric subnode, and that subnode has the same
	// _count as this node, we coalesce it into the last prunePath element. If it is
	// a "leaf" node, meaning it has a _match element, that's when it gets copied and
	// inserted into pruneTrie. Otherwise metadata and other elements are recursively
	// copied as normal.

	// Right now design assumes all nodes have a _count, zero or more alphanumeric
	// search path nodes, and optionally one of either _filter or _match.

	// Base case: we have more than one search subnode, and a count, should copy all
	// nonsearch nodes and recurse in to all search nodes.
	if (uniNode.size > 2, {
		uniNode.keysValuesDo({ |key, value|
			if (key.asString[0] !== $_, {
				pruneTrie.value(value, uniPath ++ [key], prunePath ++ [key]);
			}, {
				finalTrie.put(*(prunePath ++ [key, value]));
			});
		});
	}, {
		// Terminatimng case is we have a _match key, this node should be the node
		// that actually gets copied into the filter trie.
		if (uniNode.at(\_match).notNil, {
			// Just copy both nodes over.
			uniNode.keysValuesDo({ |key, value|
				finalTrie.put(*(prunePath ++ [key, value]));
			});
		}, {
			// Continuation case, insert nothing into the prune tree but append
			// the search key on to the last element in prunePath, then recurse
			// into the search node.
			uniNode.keysValuesDo({ |key, value|
				if (key !== \_count, {
					prunePath.wrapPut(-1,
						(prunePath.wrapAt(-1).asString ++ key.asString).asSymbol);
					pruneTrie.value(value, uniPath ++ [key], prunePath);
				});
			});
		});
	});
};

// Build descMap and idMap from the input file source.
docLines.do({ |line|
	// Ignore lines starting with comment indicator # and empty lines
	if (line.size > 0 and: { line[0] != $# }, {
		// Data line format is first some hexadecimal numbers, then a semi-colon followed
		// by a description of qualification, then a #, the space, the unicode character
		// itself, and a textual description. We want only fully-qualified unicode, for
		// those we extract the character and the text description.
		var match = line.findRegexp("([0-9A-F ]+); fully-qualified[ ]+ # (.*)");
		if (match.notNil and: { match.size == 3 }, {
			var id = match[1][1].replace($ ,"").asSymbol;
			if (excludeIds.includes(id).not, {
				var descBlock = match[2][1];
				var charSize = descBlock.find($ );
				var uniChar = descBlock[0..charSize-1].asSymbol;
				var description = descBlock[charSize+1..].toLower;
				// condition description for non-printing characters
				description = description.replace("’", "'");

				descMap.put(uniChar, description);
				idMap.put(uniChar, id);
			});
		});
	});
});

buildTrie.value([], descMap.keys, Set.new, 0);
pruneTrie.value(uniTrie.dictionary, [], []);

outFile = File.open("~/src/SCLOrkTools/classes/SCLOrkEmoji.sc".standardizePath, "w");
outFile.write("// This file generated by SCLOrkTools/scripts/build_emoji_trie.scd.
SCLOrkEmoji {
\tclassvar <trie;
\tclassvar <map;
\tclassvar <ids;

\t*load {
\t\tif (trie.isNil, {
\t\t\tSCLOrkEmoji.prLoadTrie();
\t\t});
\t\tif (map.isNil, {
\t\t\tSCLOrkEmoji.prLoadMap();
\t\t});
\t\tif (ids.isNil, {
\t\t\tSCLOrkEmoji.prLoadIds();
\t\t});
\t}

\t*prLoadTrie {
\t\ttrie = MultiLevelIdentityDictionary.new;");

finalTrie.leafDo({ | keyPath, value |
	var line = "\n\t\ttrie.put(" ++ keyPath.collect({ |x|
		"'" ++ x.asString.escapeChar($') ++ "'"
	}).join(",");
	if (value.class === IdentitySet, {
		// Append # to make for a literal array instead of a set.
		line = line ++ ", #[\n";
		value.do({ |uni|
			line = line ++ "\t\t\t'%',\t// '%', // %\n".format(
				uni, idMap.at(uni).asString, descMap.at(uni));
		});
		line = line ++ "\t\t]);";
	}, {
		line = line ++ ", %);".format(value);
	});
	outFile.write(line);
});


outFile.write("
\t}

\t*prLoadMap {
\t\tmap = IdentityDictionary.new;
");

descMap.keysValuesDo({ |uni, desc|
	var line = "\n\t\tmap.put('%', \"%\");".format(uni.asString, desc);
	outFile.write(line);
});

outFile.write("
\t}

\t*prLoadIds {
\t\tids = IdentityDictionary.new;
");

idMap.keysValuesDo({ |uni, id|
	var line = "\n\t\tids.put('%', \"%\");".format(uni.asString, id);
	outFile.write(line);
});

outFile.write("
\t}
}
");

outFile.close;
^nil
)
