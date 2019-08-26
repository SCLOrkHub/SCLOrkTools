SCLOrkUsers {
	classvar instance;

	var <userMap; // userId->Dict

	*new {
		if (instance.isNil, {
			instance = super.new.init;
		});
		^instance;
	}

	init {
		userMap = IdentityDictionary.new;
		this.update;
	}

	// Asynchronously updates the userMap with new users, if any.
	update {
		Routine.new({
			var c = Condition.new;
			var userListId, userIds;

			c.test = false;
			SCLOrkConfab.findListByName('Users', { |name, key|
				userListId = key;
				c.test = true;
				c.signal;
			});
			c.wait;

			// Now we get all the users on the list.  TODO: pagination
			c.test = false;
			SCLOrkConfab.getListNext(userListId, '0', { |id, tokens|
				// We only want the odd indices (actual ids, not list index), and strip off the terminator.
				userIds = tokens.select({|p, i| (i % 2 == 1) and: { SCLOrkConfab.idValid(p) }});
				c.test = true;
				c.signal;
			});
			c.wait;

			// Look up everybody who is not in the current userMap.
			userIds.do({ |id, index|
				if (userMap.at(id).isNil, {
					c.test = false;
					this.lookupUser(id, {
						c.test = true;
						c.signal;
					});
					c.wait;
				});
			});
		}).play;
	}

	lookupUser { |id, callback|
		if (userMap.at(id).notNil, {
			callback.value(id);
		}, {
			Routine.new({
				var c = Condition.new;
				c.test = false;
				SCLOrkConfab.findAssetById(id, { |foundId, asset|
					userMap.put(id, asset.inlineData);
					c.test = true;
					c.signal;
				});
				c.wait;
				callback.value(id);
			}).play;
		});
	}
}