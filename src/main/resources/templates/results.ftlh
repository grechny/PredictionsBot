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

<#function getRoundName round>
    <#if round.roundName?? >
        <#return round.roundName>
    <#else>
        <#local roundLocalized><@spring.message "round" /></#local>
        <#assign roundName = roundLocalized + " " + round.orderNumber>
        <#return roundName>
    </#if>
</#function>

<#function isActiveRound round>
  <#if activeRound?? >
    <#if round.orderNumber == activeRound >
      <#return true>
    </#if>
  </#if>
  <#return false>
</#function>

<#function isLive match>
  <#return match.status == 'STARTED'>
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
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/results.css">
</head>

<body>

<section>
  <h1>${competitionName}</h1>
  <div class="scroll">
  <#list rounds as round>
    <button <#if isActiveRound(round) >class="active"</#if> onclick="location.href='${baseUrl + isActiveRound(round)?then(0, round.orderNumber)}'">${getRoundName(round)?no_esc}</button>
  </#list>
  </div>
</section>

<section>
  <div class="wrapper">
    <table class="summary">
      <thead>
        <tr>
          <th></th>
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
          <td>${result.getTotalPredictions()}</td>
          <td>${(result.getTotalGuessed() / (result.getTotalPredictions()) * 100)?string("0")}%</td>
          <td <#if result.liveSum?? >class="live"</#if>>${result.getTotalSum()}<#if result.liveSum?? > (+${result.liveSum})</#if></td>
        </tr>
      </#list>
      </tbody>
    </table>
  </div>
</section>

<section>
  <#list matches as match>
  <#if matchResults[match.id.toString()]??>
  <table class="match-result">
    <thead>
      <tr>
        <th style="width: 40%">${match.homeTeam.name}</th>
        <th style="width: 20%" <#if match.status == 'STARTED' >class="live"</#if>>${match.homeTeamScore} : ${match.awayTeamScore}</th>
        <th style="width: 40%">${match.awayTeam.name}</th>
      </tr>
    </thead>
    <tbody>
    <#list matchResults[match.id.toString()] as matchResult>
      <tr <#if match.getPrediction(matchResult.user.id).get().doubleUp >class="double"</#if>>
        <td>${matchResult.user.name}</td>
        <td>${match.getPrediction(matchResult.user.id).get().predictionHome} : ${match.getPrediction(matchResult.user.id).get().predictionAway}</td>
      <#if match.status == 'STARTED' >
        <td class="live">${matchResult.liveSum}</td>
      <#else>
        <td>${matchResult.sum}</td>
      </#if>
      </tr>
    </#list>
    </tbody>
  </table>
  </#if>
  </#list>
</section>

<script type="application/javascript">
  window.Telegram.WebApp.ready();

  window.Telegram.WebApp.MainButton
      .setText("<@spring.message "buttons.close" />")
      .onClick(function () {webviewClose();})
      .show()
      .enable();

  let maxWidth = 0;
  const buttons = document.querySelectorAll(".scroll button");
  buttons.forEach(roundButton => {
    let width = parseFloat(roundButton.offsetWidth) + 1;
    if (width > maxWidth) {
      maxWidth = width;
    }
  })

  buttons.forEach(roundButton => {
    roundButton.style.width = maxWidth +'px';
  })

  let roundButton = document.querySelector(".active");
  if (roundButton === null) {
    roundButton = document.querySelector(".scroll").lastElementChild;
  }
  roundButton.scrollIntoView({
    inline: "center",
    block: "nearest"
  });

  function webviewClose() {
    window.Telegram.WebApp.close();
  }

</script>

</body>
</html>