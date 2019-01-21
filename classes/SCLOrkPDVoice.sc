// Mostly a data container member, but can reconstruct itself from a string or token list.
SCLOrkPDVoice {
	var <name;
	var <params;
	var <string; // the string that defined the voice.
	var <clockName;
	var <clockQuant;
	var <quant;

	*newFromTokens { | tokens |
		var voice = super.new.init;
		if (voice.setFromTokens(tokens).not, { ^nil });
		^voice;
	}

	init {
		params = IdentityDictionary.new;
		string = "";
	}

	// Returns false if failed to parse, true if successful.
	setFromTokens { | tokens, verbose = false |
		var state = \start;
		var parameterName;
		var parameterValueString = "";
		var parenDepth = 0;

		params.clear;

		tokens.do({ | token, index |
			// Concatenate all the tokens together to get original
			// string as parsed.
			string = string ++ token.at(\string);

			// Also append parameter value strings here, so we pick up the whitespace.
			if (state === \parameterValueStatement, {
				parameterValueString = parameterValueString ++ token.at(\string);
			});

			if (verbose, {
				"state: %, parenDepth: %, token: %".format(
					state, parenDepth, token).postln;
			});

			// We studiously ignore whitespace and comments.
			if (token.at(\type) === \whitespace
				or: { token.at(\type) === \lineComment  }
				or: { token.at(\type) === \blockComment }, {
			}, {
					state = switch(state,
						\error, { \error },
						\start, {
							if (token.at(\type) === \className
								and: { token.at(\string) == "Pbindef" }, {
									\openPbindef;
							}, {
									\error;
							});
						},
						\openPbindef, {
							if (token.at(\type) === \openParen
								and: { parenDepth == 0 }, {
								parenDepth = 1;
								\getName;
							}, {
								\error;
							});
						},
						\getName, {
							if (token.at(\type) === \symbol, {
								name = token.at(\string);
								\nameComma;
							}, {
								\error;
							});
						},
						\nameComma, {  // comma following pBindef name, or close.
							if (token.at(\type) === \comma, {
								\parameterSpace;
							}, {
								if (token.at(\type) === \closeParen
									and: { parenDepth == 1 }, {
										parenDepth = 0;
										\closePdef;
									}, {
										\error;
								});
							});
						}, \parameterSpace, {  // start a name/value pair, or close.
							if (token.at(\type) === \symbol, {
								parameterName = token.at(\string);
								\parameterNameComma;
							}, {
								if (token.at(\type) === \closeParen
									and: { parenDepth == 1}, {
										parenDepth = 0;
										\closePdef;
									}, {
										\error;
								});
							});
						},
						\parameterNameComma, {
							if (token.at(\type) === \comma, {
								\parameterValue;
							}, {
								\error;
							});
						},
						\parameterValue, {  // state means first token within value.
							parameterValueString = token.at(\string);
							case
							{ token.at(\type) === \openParen } {
								parenDepth = parenDepth + 1;
								\parameterValueStatement;
							}
							{
								token.at(\type) === \number
								or: { token.at(\type) === \string }
								or: { token.at(\type) === \global }
								or: { token.at(\type) === \className }
							} {
								\parameterValueStatement;
							}
							{ \error }
						},
						\parameterValueStatement, {
							// Pretty much anything right now is fair game in here. We're
							// looking for a closing paren. We check commas to make sure
							// they are inside of nested parens or brackets.
							case
							{ token.at(\type) === \openParen } {
								parenDepth = parenDepth + 1;
								\parameterValueStatement;
							}
							{ token.at(\type) === \closeParen } {
								parenDepth = parenDepth - 1;
								if (parenDepth > 0, {
									\parameterValueStatement
								}, {
									params.put(parameterName, parameterValueString);
									\closePdef  // Closed entire Pbindef without comma.
								});
							}
							{ token.at(\type) === \comma } {
								params.put(parameterName, parameterValueString);
								if (parenDepth == 1, {
									\parameterSpace
								}, {
									\parameterValueStatement;
								});
							}
							{ \parameterValueStatement }  // default is to keep going.
						},
						\closePdef, {
							switch (token.at(\type),
								\semiColon, { \done },
								\dot, { \chaining },
								{ \error });
						},
						\chaining, {
							// It's common/typical to chain a few calls to the
							// newly created Pbindef, so we look for a few to
							// extract the values, or error.
							if (token.at(\type) === \identifier, {
								case
								{ token.at(\string) == "play" } { \playChain }
								{ token.at(\string) == "quant"} { \quantChain }
								{ \error }
							}, {
								\error;
							});
						},
						\playChain, {
							// Brittle parser, but we expect an paren or we fail.
							if (token.at(\type) === \openParen, {
								parenDepth = parenDepth + 1;
								\playClockName;
							}, {
								\error;
							});
						},
						\playClockName, {
							// Can be a global or a local identifier, typically
							// the global ~clock.
							if (token.at(\type) === \global
								or: { token.at(\type) === \identifier }, {
									clockName = token.at(\string);
									\playClockNameComma;
								}, {
									\error;
							});
						},
						\playClockNameComma, {
							case
							{ token.at(\type) === \comma } { \playQuantArg }
							{ token.at(\type) === \closeParen } {
								parenDepth = parenDepth - 1;
								\closePdef;
							}
							{ \error }
						},
						\playQuantArg, {
							if (token.at(\type) === \identifier
								and: { token.at(\string) == "quant" }, {
									\playQuantColon;
								}, {
									\error;
							});
						},
						\playQuantColon, {
							if (token.at(\type) === \colon, {
								\playQuantValue;
							}, {
								\error;
							});
						},
						\playQuantValue, {
							if (token.at(\type) === \number, {
								clockQuant = token.at(\string).asInteger;
								\playClose;
							}, {
								\error;
							});
						},
						\playClose, {
							if (token.at(\type) === \closeParen, {
								\closePdef;
							}, {
								\error;
							});
						},
						\quantChain, {
							if (token.at(\type) === \assignment, {
								\quantValue;
							}, {
								\error;
							});
						},
						\quantValue, {
							if (token.at(\type) === \number, {
								quant = token.at(\string).asInteger;
								\closePdef;
							}, {
								\error;
							});
						},
						\done, { \error },
						{ \error }
					);
			});
		});

		^(state === \done or: { state === \closePdef });
	}
}
