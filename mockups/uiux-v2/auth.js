// videoshot mockup — 登入閘門
//
// 這個系統要求先完成 Google 第三方登入才能進入，所以閘門不能只是「首次登入畫面」，
// 每一頁都得擋。放在 <head> 而不是併進 app.js：app.js 掛在 body 尾端，等它跑到時
// 內容已經畫出來了，未登入者會先看到一閃而過的完整頁面才被踢走。
//
// 原型沒有真的 OAuth，session 就用 localStorage 假裝。login.html 自己的 script 標籤
// 帶 data-public，否則登入頁會把自己導回登入頁。
(function () {
  const KEY = 'ytspace2_user';

  function user() {
    try { return JSON.parse(localStorage.getItem(KEY) || 'null'); } catch { return null; }
  }
  function signIn(u) { localStorage.setItem(KEY, JSON.stringify(u)); }
  function signOut() { localStorage.removeItem(KEY); location.replace('login.html'); }

  window.AUTH = { user, signIn, signOut };

  // 用 in 而不是取值判斷：`data-public` 沒帶值時 dataset.public 是空字串，會被當成 false
  if (!('public' in document.currentScript.dataset) && !user()) {
    // 記下原本要去的地方，登入後直接回去，不要一律丟回首頁
    sessionStorage.setItem('ytspace2_after_login', location.pathname.split('/').pop() + location.search);
    location.replace('login.html');
  }
})();
