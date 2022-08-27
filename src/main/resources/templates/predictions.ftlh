<#import "/spring.ftl" as spring />

<#macro withTimeZone tz>
    <#local lastTZ = .time_zone>
    <#setting time_zone = tz>
    <#nested>
    <#setting time_zone = lastTZ>
</#macro>

<#function disableButton match>
  <#if (.now > match.startTime.toEpochMilli()?number_to_datetime) >
    <#return 'disabled'>
  </#if>
</#function>

<#function matchStarted match>
  <#if (.now > match.startTime.toEpochMilli()?number_to_datetime) >
    <#return 'true'>
  <#else>
    <#return 'false'>
  </#if>
</#function>

<#function matchDouble match>
  <#if match.getPrediction(user.id).isPresent() >
    <#if match.getPrediction(user.id).get().doubleUp >
      <#return 'double'>
    </#if>
  </#if>
</#function>

<#function getHomePrediction match>
  <#if match.getPrediction(user.id).isPresent() >
    <#return match.getPrediction(user.id).get().predictionHome/>
  <#else>
    <#return 0>
  </#if>
</#function>

<#function getAwayPrediction match>
  <#if match.getPrediction(user.id).isPresent() >
    <#return match.getPrediction(user.id).get().predictionAway/>
  <#else>
    <#return 0>
  </#if>
</#function>

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
      document.documentElement.className = window.Telegram.WebApp.colorScheme;
    }
    window.Telegram.WebApp.onEvent('themeChanged', setThemeClass);
    setThemeClass();
  </script>
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/predictions.css">
</head>

<body>

<section>
  <h1>${fixtures[0].season.competition.name}</h1>
  <h4>${fixtures[0].round}</h4>
</section>

<section>
<#list fixtures as match>
  <div id="${match.id}" class="match ${matchDouble(match)!}" onclick="setDouble(this)" data-started=${matchStarted(match)}>

    <span><@withTimeZone user.getTimezone().toString()>${match.startTime.toEpochMilli()?number_to_datetime?string('dd MMMM, HH:mm')}</@></span>

    <div class="row">

      <div class="column" style="padding-right: 8px">
        <img src="${match.homeTeam.logoUrl}" alt>
      </div>

      <div class="column" style="height: 40px;max-height: 40px;padding-left: 5px;padding-right: 5px;">
        <button class="plusminus" onclick="minusHome(this)" ${disableButton(match)!}>−</button>
        <span id="scoreHome" class="num">${getHomePrediction(match)}</span>
        <button class="plusminus" onclick="plusHome(this)" ${disableButton(match)!}>+</button>

        <span class="num">:</span>

        <button class="plusminus" onclick="minusAway(this)" ${disableButton(match)!}>−</button>
        <span id="scoreAway" class="num">${getAwayPrediction(match)}</span>
        <button class="plusminus" onclick="plusAway(this)" ${disableButton(match)!}>+</button>
      </div>

      <div class="column" style="padding-left: 8px">
        <img src="${match.awayTeam.logoUrl}" alt>
      </div>

    </div>
  </div>
</#list>
</section>

<script type="application/javascript">
  window.Telegram.WebApp.ready();
  init();

  function init() {
    const mainButton = window.Telegram.WebApp.MainButton;
    mainButton.setText("<@spring.message "buttons.save" />");
    mainButton.onClick(function () {save();});
    mainButton.show();

    if (document.querySelector(".double")) {
      mainButton.enable();
    } else {
      mainButton.disable();
      mainButton.color = '#d7d7d7'
    }
  }

  function save() {
    const data = [];
    document.querySelectorAll(".match").forEach((match) => {
      let matchData = {
        "matchId": match.id,
        "predictionHome": match.querySelector('#scoreHome').innerHTML,
        "predictionAway": match.querySelector('#scoreAway').innerHTML,
        "doubleUp": match.classList.contains('double')
      }
      data.push(matchData);
    });

    window.Telegram.WebApp.sendData(data);
  }

  function webviewClose() {
    window.Telegram.WebApp.close();
  }

  function setDouble(element) {
    if (event.srcElement.classList.contains('plusminus')) {
      return;
    }

    let matches = document.querySelectorAll('.match');
    for (let i = 0; i < matches.length; i++) {
      let match = matches[i];
      if (match.dataset.started === 'true' && match.classList.contains('double')) {
        console.log("Unable to change double attribute. Match already started");
        return;
      }
    }

    if (element.dataset.started === 'true') {
      console.log("Unable to set double attribute. Match already started");
      return;
    }

    if (document.querySelector('.double')) {
      document.querySelector('.double').classList.remove('double')
    }
    element.classList.add('double');

    window.Telegram.WebApp.MainButton.enable();
    window.Telegram.WebApp.MainButton.color = null;
    console.log("Double attribute has set");
  }

  function minusHome(element) {
    let score = element.parentNode.querySelector('#scoreHome');
    if (score.innerHTML === '0') {
      return;
    }

    score.innerHTML = (parseInt(score.innerHTML) - 1).toString();
  }

  function plusHome(element) {
    let score = element.parentNode.querySelector('#scoreHome');
    if (score.innerHTML === '9') {
      return;
    }

    score.innerHTML = (parseInt(score.innerHTML) + 1).toString();
  }

  function minusAway(element) {
    let score = element.parentNode.querySelector('#scoreAway');
    if (score.innerHTML === '0') {
      return;
    }

    score.innerHTML = (parseInt(score.innerHTML) - 1).toString();
  }

  function plusAway(element) {
    let score = element.parentNode.querySelector('#scoreAway');
    if (score.innerHTML === '9') {
      return;
    }

    score.innerHTML = (parseInt(score.innerHTML) + 1).toString();
  }

</script>

</body>
</html>