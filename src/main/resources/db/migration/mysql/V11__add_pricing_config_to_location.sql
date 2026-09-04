-- Per-location pricing config, synced from each slave's own application.yml
-- (JukeANatorUserInterfaceProperties) over the /ws-slave STOMP connection -- see
-- SlaveConnectionManager / LocationEventStompController. Nullable: unpopulated until a slave's
-- first sync (and never populated on standalone, which reads its own YAML directly instead of
-- this master-side cache).
ALTER TABLE location ADD COLUMN priority_cost_multiplier INT;
ALTER TABLE location ADD COLUMN credits_per_dollar INT;
ALTER TABLE location ADD COLUMN five_dollar_bonus_credits INT;
ALTER TABLE location ADD COLUMN ten_dollar_bonus_credits INT;
ALTER TABLE location ADD COLUMN web_cost_multiplier INT;
ALTER TABLE location ADD COLUMN display_currency_for_cost BOOLEAN;
