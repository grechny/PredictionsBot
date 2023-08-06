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
  <link rel="stylesheet" href="/css/style.css">
</head>

<body>

<section>
  <h1><@spring.message "leagues.button" /></h1>
  <h4><@spring.message "maintenance" /></h4>
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