// MongoDB index initialisation script
// Runs on first container startup via docker-entrypoint-initdb.d

db = db.getSiblingDB('fpl_analytics');

// bootstrap_snapshots: latest-first + TTL 72 hours
db.bootstrap_snapshots.createIndex({ fetchedAt: -1 });
db.bootstrap_snapshots.createIndex(
  { fetchedAt: 1 },
  { expireAfterSeconds: 259200, name: 'ttl_bootstrap_72h' }
);

// fixtures: by event (gameweek) and by team
db.fixtures.createIndex({ eventId: 1 });
db.fixtures.createIndex({ homeTeamId: 1, eventId: 1 });
db.fixtures.createIndex({ awayTeamId: 1, eventId: 1 });

// player_histories: unique per player
db.player_histories.createIndex({ playerId: 1 }, { unique: true });
db.player_histories.createIndex({ 'history.round': 1 });

// team_picks: unique per user+gameweek
db.team_picks.createIndex({ userId: 1, gameweekId: 1 }, { unique: true });
db.team_picks.createIndex({ gameweekId: 1 });

// league_standings: unique per league, latest-first
db.league_standings.createIndex({ leagueId: 1 }, { unique: true });
db.league_standings.createIndex({ fetchedAt: -1 });

// analytics_cache: unique per user+gameweek + TTL
db.analytics_cache.createIndex({ userId: 1, gameweekId: 1 }, { unique: true });
db.analytics_cache.createIndex(
  { ttlExpiresAt: 1 },
  { expireAfterSeconds: 0, name: 'ttl_analytics_cache' }
);

// ai_advice: by user+gameweek + TTL 7 days
db.ai_advice.createIndex({ userId: 1, gameweekId: 1 });
db.ai_advice.createIndex({ requestId: 1 }, { unique: true });
db.ai_advice.createIndex({ generatedAt: -1 });
db.ai_advice.createIndex(
  { generatedAt: 1 },
  { expireAfterSeconds: 604800, name: 'ttl_ai_advice_7d' }
);

// refresh_audit: TTL 7 days
db.refresh_audit.createIndex({ triggeredAt: -1 });
db.refresh_audit.createIndex({ topic: 1, triggeredAt: -1 });
db.refresh_audit.createIndex(
  { triggeredAt: 1 },
  { expireAfterSeconds: 604800, name: 'ttl_refresh_audit_7d' }
);

print('FPL Analytics: all indexes created successfully.');
