<#import "/spring.ftl" as spring />

<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <meta name="format-detection" content="telephone=no" />
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <meta name="MobileOptimized" content="176" />
  <meta name="HandheldFriendly" content="True" />
  <meta name="robots" content="noindex,nofollow" />
  <title></title>
  <script src="https://telegram.org/js/telegram-web-app.js"></script>
  <script>
    function setThemeClass() {
      document.documentElement.className = Telegram.WebApp.colorScheme;
    }
    Telegram.WebApp.onEvent('themeChanged', setThemeClass);
    setThemeClass();
  </script>
  <link rel="stylesheet" href="/css/style.css">
</head>

<body>

  <section>
    <h1>${competitionName}</h1>
    <h3><@spring.message "no_matches" /></h3>
    <img src="/images/football-field.png" alt=""/>
  </section>

  <script type="application/javascript">
    Telegram.WebApp.ready();

    Telegram.WebApp.MainButton
        .setText(<@spring.message "buttons.close" />)
        .show()
        .onClick(function(){ webviewClose(); });

    function webviewClose() {
      Telegram.WebApp.close();
    }

  </script>

</body>
</html>