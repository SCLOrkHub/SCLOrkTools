// Wrapper class around message format, for (de)-serializing messages
// from/to YAML, with access to data members.
SCLOrkChatMessage {
	// Server-assigned unique sender identifier.
	var <>senderId;

	// Array of recipient Ids, if [ 0 ] it's a broadcast message.
	var <>recipientIds;

	// One of:
	//   \plain - normal chat message
	//   \system - a system message
	//   \shout - a message with special highlighting (blinking until clicked)
	//   \code - source code sharing
	var <>type;

	// Plaintext contents of chat message, string.
	var <>contents;

	// Following are not part of the wire message:

	// Human-readable mapping of sender name, string.
	var <>senderName;

	// Is an echo of a message sent by this user.
	var <>isEcho;

	// If not broadcast, can be a list of names message was sent to.
	var <>recipientNames;

	*new { | senderId, recipientIds, type, contents, senderName, isEcho = false |
		^super.newCopyArgs(senderId, recipientIds, type, contents, senderName, isEcho);
	}

	*newFromDictionary { |dict, isEcho = false|
		^SCLOrkChatMessage.new(
			dict.at("senderId").asSymbol,
			dict.at("recipientIds").collect({|r| r.asSymbol }),
			dict.at("type").asSymbol,
			dict.at("contents"),
			dict.at("senderName"),
			isEcho);
	}

	postln {
		"senderId: %, recipientIds: %, type: %, contents: %, senderName: %".format(
			senderId, recipientIds, type, contents, senderName).postln;
	}

	toYAML {
		var y = "senderId: \"%\"\n".format(senderId);
		y = y ++ "recipientIds:\n";
		recipientIds.do({ |id|
			y = y ++ "  - \"%\"\n".format(id);
		});
		y = y ++ "type: \"%\"\n".format(type);
		y = y ++ "contents: %\n".format(contents.escapeChar($").replace("\n", "\\n").quote);
		y = y ++ "senderName: %".format(senderName.quote);
		^y;
	}
}
