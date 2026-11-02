package com.redmesastudios.imposter

// The page a phone gets from the QR code: pick your saved name or add one, then your secret
// word (or the news that you're the imposter), your turn to give a clue, the vote, and the
// result. It asks the TV what to show about twice a second and remembers its player id, so a
// reload or a locked phone rejoins the same seat. Plain HTML and JS, so any phone's browser
// works without installing anything. (No dollar signs in the script: this is a Kotlin string.)
fun phonePage(): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="theme-color" content="#17121F">
<title>Imposter</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body { margin: 0; height: 100%; background: #17121F; color: #F7F2FF; font: 600 17px/1.4 -apple-system, system-ui, sans-serif; }
  main { min-height: 100%; display: flex; flex-direction: column; gap: 18px; padding: max(28px, env(safe-area-inset-top)) 20px max(24px, env(safe-area-inset-bottom)); max-width: 480px; margin: 0 auto; }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 20px; }
  .brand svg { width: 34px; height: 34px; }
  h1 { margin: 0; font-size: 34px; line-height: 1.1; letter-spacing: -0.5px; font-weight: 800; }
  .soft { color: #C2B6DA; margin: 0; }
  label { font-size: 15px; font-weight: 700; color: #C2B6DA; display: block; margin-bottom: 8px; }
  input { width: 100%; height: 58px; padding: 0 16px; border-radius: 16px; border: 2px solid #4A3B66; background: #241C30; color: #F7F2FF; font: 700 22px -apple-system, system-ui, sans-serif; outline: none; }
  input:focus { border-color: #FFD25E; }
  .colors { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
  .colors button { height: 60px; border-radius: 18px; border: 0; }
  .colors button.on { box-shadow: 0 0 0 4px #17121F, 0 0 0 7px #F7F2FF; }
  .go { padding: 18px; border: 0; border-radius: 999px; background: #FFD25E; color: #17121F; font: 800 24px -apple-system, system-ui, sans-serif; }
  .go:disabled { opacity: 0.5; }
  .go.quiet { background: transparent; color: #F7F2FF; border: 2px solid #4A3B66; font-size: 20px; padding: 14px; }
  .go.red { background: #FF4D5E; color: #17121F; }
  .push { margin-top: auto; display: flex; flex-direction: column; gap: 12px; }
  .top { display: flex; justify-content: space-between; align-items: center; }
  .me { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 19px; }
  .dot { width: 38px; height: 38px; flex: none; border-radius: 50%; color: #17121F; display: flex; align-items: center; justify-content: center; font-weight: 800; }
  .dot.huge { width: 96px; height: 96px; font-size: 44px; }
  .tag { font-size: 14px; font-weight: 800; color: #C2B6DA; }
  .card { border-radius: 28px; padding: 26px 22px; background: #241C30; border: 2px solid #33284A; text-align: center; display: flex; flex-direction: column; gap: 10px; align-items: center; cursor: pointer; }
  .card.imp { border-color: #FF4D5E; background: #2E1A2A; }
  .card .word { font-size: 40px; font-weight: 800; line-height: 1.1; letter-spacing: -0.5px; }
  .card .word.red { color: #FF4D5E; }
  .card.hidden .word, .card.hidden .soft { filter: blur(14px); }
  .small { font-size: 14px; }
  .big { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; text-align: center; }
  .picks { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .pick { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 18px; border: 2px solid #4A3B66; background: #241C30; color: #F7F2FF; font: 800 19px -apple-system, system-ui, sans-serif; text-align: left; }
  .pick:active { border-color: #FFD25E; }
  .pick:disabled { opacity: 0.45; }
  .pick small { font-weight: 600; font-size: 13px; color: #C2B6DA; }
  .pick.word { justify-content: center; text-align: center; min-height: 64px; }
  .or { margin: 4px 0 -6px; }
  .problem { margin: 0; color: #FF4D5E; }
  .win { color: #4FE0B0; }
  .lose { color: #FF4D5E; }
</style>
</head>
<body>
<main id="app"></main>
<script>
var COLORS = ['#4DB5FF', '#FF7A66', '#4FE0B0', '#FFD25E', '#B79BFF', '#FF8FC7', '#B8E55C', '#FFA44F'];
var params = new URLSearchParams(location.search);
var pin = params.get('pin') || '';
var idKey = 'imposter-id-' + pin;
var id = localStorage.getItem(idKey);
var color = parseInt(localStorage.getItem('imposter-color') || '0', 10);
var shown = '';
var last = null;
// The word card is hidden during the clues so nobody peeks; tap it to look.
var hiddenFor = { game: -1, hidden: false, clued: false };
var app = document.getElementById('app');

function esc(text) { var d = document.createElement('div'); d.textContent = text; return d.innerHTML; }
function brand() { return '<div class="brand"><svg viewBox="0 0 84 84" aria-hidden="true"><circle cx="42" cy="42" r="40" fill="#FF4D5E"/><path d="M12 42 C20 30 30 25 42 25 C54 25 64 30 72 42 C64 54 54 59 42 59 C30 59 20 54 12 42 Z" fill="#17121F"/><circle cx="42" cy="42" r="11" fill="#fff"/><circle cx="42" cy="42" r="5" fill="#17121F"/></svg>Imposter</div>'; }
function dot(name, c, big) { return '<span class="dot' + (big ? ' huge' : '') + '" style="background:' + COLORS[c] + '">' + esc(name.charAt(0).toUpperCase()) + '</span>'; }
function header(v, note) {
  return '<div class="top"><div class="me">' + dot(v.name, v.color) + esc(v.name) + '</div><span class="tag">' + (note || '') + '</span></div>';
}
function list(names) {
  if (names.length < 2) return names.join('');
  return names.slice(0, -1).join(', ') + ' and ' + names[names.length - 1];
}

// What you are this game: the word (crew, or an imposter with a close word who doesn't know
// it), or the imposter card.
function card(v, hidden) {
  var inner;
  if (v.imposter) {
    inner = '<p class="soft small">You are</p><div class="word red">THE IMPOSTER</div>' +
      (v.category ? '<p class="soft">The category is ' + esc(v.category) + '</p>' : '<p class="soft">Nobody will tell you the word. Blend in.</p>') +
      (v.partners.length ? '<p class="soft small">Also an imposter: ' + esc(list(v.partners)) + '</p>' : '');
  } else {
    inner = '<p class="soft small">The secret word</p><div class="word">' + esc(v.word) + '</div><p class="soft">' + esc(v.category) + '</p>';
  }
  return '<div class="card' + (v.imposter ? ' imp' : '') + (hidden ? ' hidden' : '') + '" id="card">' + inner +
    '<p class="soft small" style="filter:none">' + (hidden ? 'Tap to look' : 'Tap to hide') + '</p></div>';
}

function showCode() {
  app.innerHTML = brand() + '<h1>Enter the code on the TV</h1><div><label for="code">Code</label><input id="code" inputmode="numeric" maxlength="4" autocomplete="off"></div><div class="push"><button class="go" id="go">Next</button></div>';
  document.getElementById('go').onclick = function () {
    location.search = '?pin=' + encodeURIComponent(document.getElementById('code').value.trim());
  };
}

function showJoin() {
  app.innerHTML = brand() + '<p class="soft">Loading…</p>';
  fetch('/api/players?pin=' + encodeURIComponent(pin), { cache: 'no-store' })
    .then(function (r) { return r.json(); })
    .then(function (r) { joinForm(r.players || [], ''); })
    .catch(function () { joinForm([], ''); });
}

// Saved players first (tap your name), then a form for someone new.
function joinForm(players, problem) {
  var name = localStorage.getItem('imposter-name') || '';
  var mine = localStorage.getItem('imposter-profile') || '';
  players.sort(function (a, b) { return (b.id === mine) - (a.id === mine); });
  var swatches = COLORS.map(function (c, i) {
    return '<button type="button" aria-label="Color ' + (i + 1) + '" data-i="' + i + '" class="' + (i === color ? 'on' : '') + '" style="background:' + c + '"></button>';
  }).join('');
  var saved = players.map(function (p) {
    return '<button type="button" class="pick" data-id="' + esc(p.id) + '"' + (p.playing ? ' disabled' : '') + '>' + dot(p.name, p.color) +
      '<span>' + esc(p.name) + (p.playing ? '<small> · already in</small>' : '') + '</span></button>';
  }).join('');
  app.innerHTML = brand() + '<h1>' + (players.length ? 'Who\'s playing?' : 'Join the game on the TV') + '</h1>' +
    (problem ? '<p class="problem">' + esc(problem) + '</p>' : '') +
    (players.length ? '<div class="picks">' + saved + '</div><p class="soft or">Someone new?</p>' : '') +
    '<div><label for="name">First name</label><input id="name" maxlength="14" autocomplete="given-name" value="' + (players.length ? '' : esc(name)) + '"></div>' +
    '<div><label>Favorite color</label><div class="colors">' + swatches + '</div></div>' +
    '<div class="push"><button class="go" id="join">Join</button></div>';
  Array.prototype.forEach.call(document.querySelectorAll('.colors button'), function (b) {
    b.onclick = function () {
      color = parseInt(b.getAttribute('data-i'), 10);
      Array.prototype.forEach.call(document.querySelectorAll('.colors button'), function (o) { o.className = o === b ? 'on' : ''; });
    };
  });
  Array.prototype.forEach.call(document.querySelectorAll('.pick'), function (b) {
    b.onclick = function () { join({ profile: b.getAttribute('data-id') }); };
  });
  document.getElementById('join').onclick = function () {
    var picked = document.getElementById('name').value.trim();
    if (!picked) { document.getElementById('name').focus(); return; }
    localStorage.setItem('imposter-name', picked);
    localStorage.setItem('imposter-color', String(color));
    join({ name: picked, color: color });
  };
}

function join(body) {
  Array.prototype.forEach.call(document.querySelectorAll('button'), function (b) { b.disabled = true; });
  body.id = id || '';
  post('/api/join', body).then(function (r) {
    if (!r.id) {
      return fetch('/api/players?pin=' + encodeURIComponent(pin), { cache: 'no-store' }).then(function (x) { return x.json(); })
        .then(function (x) { joinForm(x.players || [], 'Someone else is playing as that name. Pick another.'); });
    }
    id = r.id;
    localStorage.setItem(idKey, id);
    localStorage.setItem('imposter-profile', r.profile);
    shown = '';
    poll();
  });
}

function render(v, force) {
  var key = JSON.stringify(v);
  if (key === shown && !force) return;
  // The join form stays put while someone types.
  if (v.phase === 'join' && shown.indexOf('"join"') >= 0) return;
  shown = key;
  last = v;
  if (v.phase === 'join') return showJoin();
  if (hiddenFor.game !== v.game) hiddenFor = { game: v.game, hidden: false, clued: false };
  var html = '';
  var after = null;
  if (v.phase === 'lobby') {
    html = header(v) + '<div class="big"><h1>You\'re in!</h1><p class="soft">The game starts from the TV once everyone has joined.</p><p class="soft small">' +
      v.players + (v.players === 1 ? ' player' : ' players') + ' so far · ' + esc(v.summary) + '</p></div>';
  } else if (v.phase === 'watching') {
    html = header(v) + '<div class="big"><h1>A game is on</h1><p class="soft">You\'ll be dealt into the next one.</p></div>';
  } else if (v.out && (v.phase === 'clues' || v.phase === 'vote')) {
    html = header(v, 'OUT') + '<div class="big"><h1>You\'re out</h1><p class="soft">No talking! Watch the TV to see how it ends.</p></div>' + card(v, hiddenFor.hidden);
    after = cardToggle;
  } else if (v.phase === 'deal') {
    html = header(v) + card(v, hiddenFor.hidden) + (v.ready
      ? '<p class="soft" style="text-align:center">Waiting for everyone to look…</p>'
      : '<div class="push"><button class="go" id="ready">Got it</button></div>');
    after = function () {
      cardToggle();
      var b = document.getElementById('ready');
      if (b) b.onclick = function () { act({ action: 'ready' }); };
    };
  } else if (v.phase === 'clues') {
    // The card hides when the clues start, so nobody sees it over a shoulder.
    if (!hiddenFor.clued) hiddenFor = { game: v.game, hidden: true, clued: true };
    html = header(v, 'ROUND ' + v.round) + (v.myTurn
      ? '<div class="big"><h1>Your turn!</h1><p class="soft">Say one word out loud that fits ' + (v.imposter ? 'what everyone else seems to have.' : 'the secret word, without giving it away.') + '</p></div>' +
        card(v, hiddenFor.hidden) + '<div class="push"><button class="go" id="done">Done</button></div>'
      : '<div class="big">' + dot(v.speaker, v.speakerColor, true) + '<h1>' + esc(v.speaker) + '\'s turn</h1><p class="soft">Listen for a clue that doesn\'t fit.</p></div>' +
        card(v, hiddenFor.hidden) + '<div class="push"><button class="go quiet" id="call">I know who it is · call a vote</button></div>');
    after = function () {
      cardToggle();
      var d = document.getElementById('done');
      if (d) d.onclick = function () { act({ action: 'done' }); };
      var c = document.getElementById('call');
      if (c) c.onclick = function () {
        if (c.getAttribute('data-sure')) act({ action: 'call' });
        else { c.setAttribute('data-sure', '1'); c.className = 'go red'; c.textContent = 'Tap again to call the vote'; }
      };
    };
  } else if (v.phase === 'vote') {
    if (v.voted) {
      html = header(v) + '<div class="big"><h1>Vote is in</h1><p class="soft">' + (v.mine === 'skip' ? 'You skipped.' : 'You voted for ' + esc(v.mine) + '.') + ' Waiting for everyone…</p></div>';
    } else {
      html = header(v, 'VOTE') + '<h1>Who\'s the imposter?</h1>' + (v.calledBy ? '<p class="soft">' + esc(v.calledBy) + ' called the vote.</p>' : '') +
        '<div class="picks">' + v.candidates.map(function (p) {
          return '<button type="button" class="pick" data-id="' + esc(p.id) + '">' + dot(p.name, p.color) + '<span>' + esc(p.name) + '</span></button>';
        }).join('') + '</div><div class="push"><button class="go quiet" id="skip">Skip my vote</button></div>';
      after = function () {
        Array.prototype.forEach.call(document.querySelectorAll('.pick'), function (b) {
          b.onclick = function () { act({ action: 'vote', target: b.getAttribute('data-id') }); };
        });
        document.getElementById('skip').onclick = function () { act({ action: 'vote', target: '' }); };
      };
    }
  } else if (v.phase === 'result') {
    html = header(v) + '<div class="big"><h1>Look at the TV!</h1><p class="soft">The votes are in.</p></div>';
  } else if (v.phase === 'guess') {
    if (v.guessing) {
      html = header(v, 'CAUGHT') + '<h1>You\'re caught!</h1><p class="soft">Guess the secret word to win it anyway. One try.</p><div class="picks">' +
        v.choices.map(function (w) { return '<button type="button" class="pick word" data-w="' + esc(w) + '">' + esc(w) + '</button>'; }).join('') + '</div>';
      after = function () {
        Array.prototype.forEach.call(document.querySelectorAll('.pick'), function (b) {
          b.onclick = function () { act({ action: 'guess', word: b.getAttribute('data-w') }); };
        });
      };
    } else {
      html = header(v) + '<div class="big"><h1>' + esc(v.guesser) + ' gets one guess</h1><p class="soft">Look at the TV!</p></div>';
    }
  } else if (v.phase === 'final') {
    var crew = v.winner === 'crew';
    html = header(v) + '<div class="big"><p class="soft">' + esc(v.ending) + '</p><h1 class="' + (v.won ? 'win' : 'lose') + '">' + (v.won ? 'You win!' : 'You lose') + '</h1>' +
      '<p>' + (crew ? 'The crew wins.' : (v.imposters.length > 1 ? 'The imposters win.' : 'The imposter wins.')) + '</p>' +
      '<p class="soft">The word was <b>' + esc(v.secret) + '</b> (' + esc(v.secretCategory) + ')' + (v.closeWord ? ', and the imposter\'s was <b>' + esc(v.closeWord) + '</b>' : '') + '.</p>' +
      '<p class="soft">' + (v.imposters.length > 1 ? 'The imposters were ' : 'The imposter was ') + esc(list(v.imposters)) + '.</p></div>';
  }
  app.innerHTML = html;
  if (after) after();
}

function cardToggle() {
  var c = document.getElementById('card');
  if (c) c.onclick = function () { hiddenFor.hidden = !hiddenFor.hidden; render(last, true); };
}

function act(body) {
  Array.prototype.forEach.call(document.querySelectorAll('button'), function (b) { b.disabled = true; });
  body.id = id;
  post('/api/act', body).then(function (v) { render(v); }).catch(function () { render(last, true); });
}

function post(path, body) {
  body.pin = pin;
  return fetch(path, { method: 'POST', body: JSON.stringify(body) }).then(function (r) { return r.json(); });
}

var timer = null;
function poll() {
  clearTimeout(timer);
  fetch('/api/state?pin=' + encodeURIComponent(pin) + '&id=' + encodeURIComponent(id || ''), { cache: 'no-store' })
    .then(function (r) { if (r.status === 403) { showCode(); throw new Error('code'); } return r.json(); })
    .then(function (v) { render(v); timer = setTimeout(poll, 600); })
    .catch(function (e) { if (e.message !== 'code') timer = setTimeout(poll, 2000); });
}

if (!pin) showCode(); else poll();
</script>
</body>
</html>
"""
