SCLOrkPDPreset {
	var <name;
	var <voices;

	*newFromFile { | filePath, name |
		var voices = SCLOrkPDParser.parseFile(filePath);
		if (voices.notNil, {
			^super.newCopyArgs(name, voices).init;
		}, {
			^nil;
		});
	}

	init {
	}

	voiceAt { | index |
		^voices.wrapAt(index);
	}
}
