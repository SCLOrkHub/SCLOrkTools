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

(
var count = 0;
SCLOrkEmoji.load;
SCLOrkEmoji.trie.at(*[\_count]).postln;
)

(
~oldAction = ~picker.actions[0];
~picker.insertAction(~oldAction,
	Menu(MenuAction("loofa"), MenuAction("pooty")).title_("snooty"));
~picker.removeAction(~oldAction);
~picker.front;
)

(
var a = [\a, \b, \c, \d];
a.wrapPut(-1, (a.wrapAt(-1).asString ++ "e").asSymbol);
a.postln;
)