SCLOrkPDPreset {
	var <>name;
	var <year;
	var <voices;

	*newFromFile { | filePath verbose = false |
		var tokens, tokenIndex, parseError, voices, year, openVoice;
		var fileString = String.readNew(File.new(filePath, "r"));
		tokens = SCLOrkPDParser.tokenize(fileString);
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
					if (newVoice.isNil, {
						// Reparse with verbose logging turned on.
						if (verbose, {
							SCLOrkPDVoice.newFromTokens(openVoice, verbose: true);
						});
						^nil;
					});
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

				// Extract sample year from comment.
				if (token.at(\type) === \lineComment
					and: { token.at(\string).beginsWith(
						"// Most recent year sampled:")
					or: { token.at(\string).beginsWith(
						"// Most recent voice sampled:") }
					}, {
						var split = token.at(\string).split($ );
						if (split.size >= 6, {
							year = split[5].asInteger;
						});
				});
			});
		});

		// Unterminated Pbindef is an error.
		if (openVoice.notNil, {
			"*** error, unterminated Pbindef %".format(openVoice).postln;
			voices = nil;
		});

		if (voices.notNil, {
			^super.newCopyArgs(name, year, voices);
		}, {
			^nil;
		});
	}

	voiceAt { | index |
		^voices.wrapAt(index);
	}
}
