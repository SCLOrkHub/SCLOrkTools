SCLOrkAsset {
	var <key;
	var <type;
	var <name;
	var <inlineData;

	*newFromArgs { |key, type, name, author, inlineData|
		^super.newCopyArgs(key, type, name, author, inlineData).init;
	}

	init {
		// Sort out inlineData dependent on Asset type.
		if (inlineData.class === Int8Array, {
			if (type === \snippet, {
				var chars = Array.fill(inlineData.size, { |i| inlineData[i].asAscii });
				inlineData = String.newFrom(chars);
			});
			if (type === \yaml, {
				var chars = Array.fill(inlineData.size, { |i| inlineData[i].asAscii });
				var dataString = String.newFrom(chars);
				inlineData = dataString.parseYAML;
			});
		});
	}
}
