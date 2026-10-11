const path = require('path');
const mc = require(path.resolve(__dirname, '../.tools/protocol/node_modules/minecraft-protocol'));
const [,, port, version] = process.argv;
const data = require(path.resolve(__dirname, '../.tools/protocol/node_modules/minecraft-data'));
const protocol = data.versions.pc.find(v => v.minecraftVersion === version).version;
const schema = data(version)?.version.version === protocol ? version : data.supportedVersions.pc.find(v => data(v)?.version.version === protocol) || version;
const options = { host: '127.0.0.1', port: Number(port), username: 'Hash014Test', auth: 'offline', version: schema, disableChatSigning: true, clientSettings: { viewDistance: 2 } };
const client = mc.createClient(options);
// A few library aliases share a packet schema but incorrectly inherit its handshake number.
options.protocolVersion = protocol;
let maps = 0, lastPosition;
client.on('error', err => { console.error('CLIENT ERROR', err.stack); process.exitCode = 1; });
client.on('end', reason => { console.log('CLIENT END', reason, 'maps=' + maps); process.exit(); });
client.on('disconnect', p => console.log('CLIENT DISCONNECT', JSON.stringify(p)));
client.on('login', () => console.log('CLIENT LOGIN', client.version));
client.on('playerJoin', () => console.log('CLIENT JOIN'));
client.on('position', p => {
  if (p.teleportId !== undefined) client.write('teleport_confirm', { teleportId: p.teleportId });
  else if (version.startsWith('1.7.') || version.startsWith('1.8.')) {
    // Legacy clients acknowledge teleports with their feet/eye position.
    const feet = version.startsWith('1.7.') ? p.y - 1.62 : p.y;
    lastPosition = { x: p.x, y: feet, stance: feet + 1.62,
      z: p.z, yaw: p.yaw, pitch: p.pitch, onGround: false };
    client.write('position_look', lastPosition);
  }
});
setInterval(() => { if (lastPosition && client.state === 'play') client.write('position_look', lastPosition); }, 50).unref();
client.on('map', p => { maps++; console.log('CLIENT MAP', p.itemDamage ?? p.mapId, 'bytes=' + (p.data?.length ?? 0)); });
client.on('systemChat', p => { const text = JSON.stringify(p); if (text.includes('Reforged') || text.includes('Erreur')) console.log('CLIENT CHAT', text); });
setTimeout(() => { console.error('CLIENT TIMEOUT'); client.end(); }, 150000).unref();
