<#import "/spring.ftl" as spring />

<#macro withTimeZone tz>
    <#local lastTZ = .time_zone>
    <#setting time_zone = tz>
    <#nested>
    <#setting time_zone = lastTZ>
</#macro>

<#function userRow result>
  <#if (user.id == result.user.id) >
    <#return 'user-row'>
  </#if>
</#function>

<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/html">
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
      document.documentElement.className = window.Telegram.WebApp.colorScheme;
    }
    window.Telegram.WebApp.onEvent('themeChanged', setThemeClass);
    setThemeClass();
  </script>
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/results.css">
</head>

<body>

<section>
  <h1>${competitionName}</h1>
</section>

<section>
  <div class="wrapper">
    <table>
      <thead>
        <tr>
          <th><@spring.message "results.rank" /></th>
          <th><@spring.message "results.name" /></th>
          <th><@spring.message "results.predictions.total" /></th>
          <th><@spring.message "results.predictions.success" /></th>
          <th><@spring.message "results.points" /></th>
        </tr>
      </thead>
      <tbody>
      <#list results as result>
        <tr class="${userRow(result)!}">
          <td>${result?counter}</td>
          <td>${result.user.name}</td>
          <td>${result.predictions}</td>
          <td>${(result.guessed / result.predictions * 100)?string("0")}%</td>
          <td>${result.sum}</td>
        </tr>
      </#list>
      </tbody>
    </table>
  </div>
</section>

<script type="application/javascript">
  window.Telegram.WebApp.ready();

  window.Telegram.WebApp.MainButton
      .setText("<@spring.message "buttons.close" />")
      .onClick(function () {webviewClose();})
      .show()
      .enable();

  function webviewClose() {
    window.Telegram.WebApp.close();
  }

</script>

</body>
</html>