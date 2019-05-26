SCLOrkEmojiPickerView : TextView {
	var sclorkChat;
	var searchPath;
	var searchString;

	*new { |fontSize, sclorkChat, maxHeight|
		// Load the Emoji trie and map.
		SCLOrkEmoji.load;

		^super.new.init(fontSize, sclorkChat, maxHeight);
	}

	init { |fontSize, sclorkChat, maxHeight|
		sclorkChat = sclorkChat;

		this.editable = false;
		this.font = Font(Font.defaultSansFace, fontSize);

		this.hidePicker;
	}

	showPicker {
		this.visible = true;
	}

	hidePicker {
		this.visible = false;
	}

	setSearchString { |search|

	}

}