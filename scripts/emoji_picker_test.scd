(
// Build Emoji data structures before building UI.
SCLOrkEmoji.load;

~w = Window.new("Emoji Picker Test",
	Rect.new(100, 100, 250, 100),
	false,
	true);
~w.layout = VLayout.new(
	HLayout.new(
		~showButton = Button.new,
		~hideButton = Button.new
	),
	nil
);

~picker = SCLOrkEmojiMenu.new(SCLOrkEmoji.trie.dictionary, "", true);

~showButton.string = "show";
~showButton.action = { ~picker.front };
~hideButton.string = "hide";

~w.front;
)
