// Plays a scripted game with a real Minecraft client (mineflayer) against the test server.
// Usage: node bot-test.js [host] [port]   (the bot must be op: the smoke test runs "op NgnlBot")
const mineflayer = require('mineflayer');

const HOST = process.argv[2] || 'localhost';
const PORT = parseInt(process.argv[3] || '25599', 10);
const failures = [];
let deaths = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function check(name, condition, detail = '') {
  console.log(`${condition ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
  if (!condition) failures.push(name);
}

const bot = mineflayer.createBot({ host: HOST, port: PORT, username: 'NgnlBot', version: '1.21.4', auth: 'offline' });
const chatLog = [];
bot.on('messagestr', (message) => chatLog.push(message));
bot.on('death', () => { deaths++; });
bot.on('kicked', (reason) => console.log('KICKED', reason));
bot.on('error', (error) => console.log('ERROR', error.message));
bot.on('resourcePack', () => { console.log('resource pack offered: accepting'); bot.acceptResourcePack(); });

/** Wait for the next inventory window and return its title text. */
function nextWindow(timeoutMs = 15000) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => { bot.removeListener('windowOpen', handler); resolve(null); }, timeoutMs);
    const handler = (window) => { clearTimeout(timer); resolve(window); };
    bot.once('windowOpen', handler);
  });
}

function titleOf(window) {
  try { return typeof window.title === 'string' ? window.title : JSON.stringify(window.title); }
  catch (e) { return String(window.title); }
}

const hasGlyph = (text) => /[-]/.test(text || '');
const say = (command) => bot.chat(command);

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  console.log('bot spawned at', bot.entity.position.toString());
  await sleep(4000); // the server sends the resource pack 1 s after the join

  check('joined the lobby platform', bot.entity.position.y > 90, `y=${bot.entity.position.y}`);

  say('/ngnl start');
  await sleep(15000);
  check('game started', chatLog.some((line) => line.includes('has begun')));
  check('teleported to the mining world', Math.abs(bot.entity.position.y) < 400 && bot.entity.position.y < 300);

  // --- role card (picture version: the pack is accepted) ---
  const firstWindow = nextWindow();
  say('/ngnl setrole NgnlBot SORA');
  const card = await firstWindow;
  check('role card opens when the role is revealed', card !== null);
  if (card) {
    console.log('card title:', JSON.stringify(titleOf(card)).slice(0, 160));
    check('card title is made of card glyphs (picture card)', hasGlyph(titleOf(card)), 'if this fails the pack status was not received');
    check('card has 54 slots', card.slots.length >= 54, `slots=${card.slots.length}`);

    const flipped = nextWindow();
    bot.clickWindow(8, 0, 0); // ">" button
    const back = await flipped;
    check('flip button shows the powers page', back !== null && hasGlyph(titleOf(back)) && titleOf(back) !== titleOf(card));
    bot.closeWindow(bot.currentWindow || back || card);
  }

  const reopened = nextWindow();
  say('/role');
  check('/role reopens the card', (await reopened) !== null);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);

  // --- a lethal hit never kills during the mining phase ---
  say('/effect give NgnlBot minecraft:instant_damage 1 20');
  await sleep(3000);
  check('lethal damage does not kill in the mining phase', deaths === 0 && bot.health > 0, `health=${bot.health} deaths=${deaths}`);

  // --- finale ---
  say('/ngnl forcearena');
  await sleep(10000);
  check('arena phase announced', chatLog.some((line) => line.includes('LOVE FIGHT')));
  const names = bot.inventory.items().map((item) => item.name);
  check('finale items received (Sora crown = golden helmet)', names.includes('golden_helmet'), names.join(','));
  check('arena weapon received (Love Gun)', names.some((name) => name.includes('hoe') || name.includes('bow')), names.join(','));

  // --- every role: reveal, finale powers, and use of every item (errors are caught by the server log check) ---
  const ROLES = ['SORA','SHIRO','STEPHANIE','MAKOTO','RIKU','SCHWI','IZUNA','INO','KURAMI','FEEL','FIEL','CHLAMMY',
    'SHI','KU','IVAN','NONNA','CORONE','TETO','EINZIG','JIBRIL','AZRIEL','PLUM','THINK','ARTOSH','OKEIN','KAINAS',
    'HOLOU','MIKO','GHOST'];
  let assigned = 0;
  // Client-side physics may produce NaN positions after server-forced velocity/teleports (mineflayer quirk that
  // makes the server kick with "Invalid move player packet"): freeze the client physics during this stress loop.
  bot.physicsEnabled = false;
  for (const role of ROLES) {
    say('/effect clear NgnlBot');
    say('/effect give NgnlBot minecraft:resistance 60 4 true');
    say('/effect give NgnlBot minecraft:instant_health 1 10 true');
    say('/clear NgnlBot');
    await sleep(500);
    say(`/ngnl setrole NgnlBot ${role}`);
    await sleep(900);
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    for (const item of bot.inventory.items()) {
      try {
        await bot.equip(item, 'hand');
        bot.activateItem();
        await sleep(200);
        if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      } catch (e) { /* item cannot be equipped: ignore */ }
    }
    if (chatLog.slice(-8).some((line) => line.includes(role.charAt(0) + role.slice(1).toLowerCase()) || line.includes('Ton r'))) assigned++;
  }
  bot.physicsEnabled = true;
  check('all roles could be assigned and used without kicking the bot', bot.entity !== undefined && deaths === 0, `deaths=${deaths}`);
  check('role reveal message seen for most roles', assigned >= 20, `${assigned}/${ROLES.length}`);

  // --- end of the game ---
  say('/ngnl stop');
  await sleep(8000);
  check('back in the lobby after the game', bot.entity.position.y > 90, `y=${bot.entity.position.y}`);
}

run().then(() => {
  console.log(failures.length ? `BOT TEST FAILED (${failures.length}): ${failures.join('; ')}` : 'BOT TEST PASSED');
  bot.quit();
  setTimeout(() => process.exit(failures.length ? 1 : 0), 1000);
}).catch((error) => {
  console.log('BOT TEST CRASHED', error);
  process.exit(2);
});
setTimeout(() => { console.log('BOT TEST TIMEOUT'); process.exit(3); }, 420000);
