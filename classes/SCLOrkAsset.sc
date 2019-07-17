SCLOrkAsset {
	var <key;
	var <type;
	var <name;
	var <fileExtension;
	var <author;
	var <deprecatedBy;
	var <deprecates;
	var <inlineData;

	*newFromArgs { |key, type, name, fileExtension, author, deprecatedBy, deprecates, inlineData|
		^super.newCopyArgs(key, type, name, fileExtension, author, deprecatedBy, deprecates, inlineData).init;
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
}