SCLOrkPDParser {
	*parseFile { | filePath |
		var fileString = String.readNew(File.new(filePath, "r"));
		^SCLOrkPDParser.parse(fileString);
	}

	*parse { | string |
		var tokens, tokenIndex, parseError, voices, openVoice;

		// Tokenize string.
		tokens = SCLOrkPDParser.tokenize(string);
		if (tokens.isNil, { ^nil; });

		// We now advance through the tokens, extracting Pbindef objects
		// and ignoring most other things.
		tokenIndex = 0;
		voices = Array.new;
		openVoice = nil;

		tokens.do({ | token, index |
			// If we're inside an open Pbindef we're looking for a semicolon to
			// terminate, so append everything to the current token list until then.
			if (openVoice.notNil, {
				openVoice = openVoice.add(token);
				if (token.at(\type) === \semiColon, {
					var newVoice = SCLOrkPDVoice.newFromTokens(openVoice);
					if (newVoice.isNil, { ^nil; });
					voices = voices.add(newVoice);
					openVoice = nil;
				});
			}, {
				// Otherwise we'll ignore all other SuperCollider code in the file
				// until we encounter another Pbindef.
				if (token.at(\type) === \className
					and: { token.at(\string) == "Pbindef" }, {
						openVoice = [ token ];
				});
			});
		});

		// Unterminated Pbindef is an error.
		if (openVoice.notNil, {
			"*** error, unterminated Pbindef %".format(openVoice).postln;
			^nil;
		}, {
			^voices;
		});
	}

	*tokenize { | string |
		var parseError = false;
		var offset = 0;
		var tokens = Array.new;

		while ({ offset < string.size and: { parseError.not }}, {
			var match = nil;
			var type = nil;

			type = case
			{
				match = string.findRegexpAt("[ \t\n]+", offset);
				match.notNil;
			} { \whitespace }
			{
				match = string.findRegexpAt("//[^\n]*\n", offset);
				match.notNil;
			} { \lineComment }
			{
				match = string.findRegexpAt("/[*].[*]/", offset);
				match.notNil;
			} { \blockComment }
			{
				match = string.findRegexpAt("\\\\[a-zA-Z0-9_]+", offset);
				match.notNil;
			} { \symbol }
			{
				match = string.findRegexpAt("'[^']*'", offset);
				match.notNil;
			} { \symbol }
			{
				match = string.findRegexpAt("[(]", offset);
				match.notNil;
			} { \openParen }
			{
				match = string.findRegexpAt("[)]", offset);
				match.notNil;
			}  { \closeParen }
			{
				match = string.findRegexpAt("\"[^\"]*\"", offset);
				match.notNil;
			} { \string }
			{
				match = string.findRegexpAt("[-+]?[0-9]+[.]?[0-9]*", offset);
				match.notNil;
			} { \number }
			{
				match = string.findRegexpAt("\\[", offset);
				match.notNil;
			} { \openBracket }
			{
				match = string.findRegexpAt("\\]", offset);
				match.notNil;
			} { \closeBracket }
			{
				match = string.findRegexpAt("[a-z]+[A-Za-z0-9_]*", offset);
				match.notNil;
			} { \identifier }
			{
				match = string.findRegexpAt("~[A-Za-z0-9_]+", offset);
				match.notNil;
			} { \global }
			{
				match = string.findRegexpAt("[A-Z]+[A-Za-z0-9_]+", offset);
				match.notNil;
			} { \className }
			{
				match = string.findRegexpAt("[.]", offset);
				match.notNil;
			} { \dot }
			{
				match = string.findRegexpAt(",", offset);
				match.notNil;
			} { \comma }
			{
				match = string.findRegexpAt("===", offset);
				match.notNil;
			} { \identityComparison }
			{
				match = string.findRegexpAt("==", offset);
				match.notNil;
			} { \equality }
			{
				match = string.findRegexpAt("=", offset);
				match.notNil;
			} { \assignment }
			{
				match = string.findRegexpAt(":", offset);
				match.notNil;
			} { \colon }
			{
				match = string.findRegexpAt(";", offset);
				match.notNil;
			} { \semiColon }
			{
				match = string.findRegexpAt("/", offset);
				match.notNil;
			} { \forwardSlash }
			{
				match = string.findRegexpAt("[*]", offset);
				match.notNil;
			} { \asterisk }
			{
				match = string.findRegexpAt("-", offset);
				match.notNil;
			} { \hyphen }
			{
				match = string.findRegexpAt("[+]", offset);
				match.notNil;
			} { \plus };

			if (type.isNil or: { match.isNil }, {
				parseError = true;
				"*** SCLOrkPD tokenizer choked around: %".format(
					string[offset-25..offset+25]).postln;
			}, {
				var node = ();
				node.put(\offset, offset);
				node.put(\string, match[0]);
				node.put(\length, match[1]);
				node.put(\type, type);
				tokens = tokens.add(node);
				offset = offset + match[1];
			});
		});

		if (parseError, {
			^nil;
		}, {
			^tokens;
		});
	}
}
