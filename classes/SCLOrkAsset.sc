SCLOrkAsset {
	var <key;
	var <type;
	var <name;
	var <author;
	var <deprecatedBy;
	var <deprecates;
	var <inlineData;

	*newFromArgs { |key, type, name, author, deprecatedBy, deprecates, inlineData|
		^super.newCopyArgs(key, type, name, author, deprecatedBy, deprecates, inlineData).init;
	}

	init {
		// Sort out inlineData dependent on Asset type.
		if (inlineData.class === Int8Array, {
			if (type === \snippet, {
				var chars = Array.fill(inlineData.size, { |i| inlineData[i].asAscii });
				inlineData = String.newFrom(chars);
			});
		});
	}

	// base class,
	asYAML {

	}
}

SCLOrkInlineAsset {
}

SCLOrkFileAsset {
}

// What if SCLOrkAssets were all either YAML strings or files? Maybe AssetType is just a label?
SCLOrkPerson : SCLOrkAsset {
	var <realName;
	var <defaultNick;

	*newFromParse { |parse|

	}

	asYAML {

	}
}