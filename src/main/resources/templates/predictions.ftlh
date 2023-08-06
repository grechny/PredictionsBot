<#import "/spring.ftl" as spring />

<#function getStartTime match>
  <#if (match.startTime??) >
    <#local lastTZ = .time_zone>
    <#setting time_zone = user.getTimezone().toString()>
    <#assign matchStartTime = match.startTime.toEpochMilli()?number_to_datetime?string('dd MMMM, HH:mm')>
    <#setting time_zone = lastTZ>
    <#return matchStartTime />
  <#else>
    <#local time_tbd><@spring.message "time_tbd" /></#local>
    <#return time_tbd />
  </#if>
</#function>

<#function disableButton match>
  <#if (match.startTime??) && (.now > match.startTime.toEpochMilli()?number_to_datetime) >
    <#return 'disabled'>
  </#if>
</#function>

<#function matchStarted match>
  <#if (match.startTime??) && (.now > match.startTime.toEpochMilli()?number_to_datetime) >
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
  <#elseif matchStarted(match) == 'true' >
    <#return '−'>
  <#else>
    <#return 0>
  </#if>
</#function>

<#function getAwayPrediction match>
  <#if match.getPrediction(user.id).isPresent() >
    <#return match.getPrediction(user.id).get().predictionAway/>
  <#elseif matchStarted(match) == 'true' >
    <#return '−'>
  <#else>
    <#return 0>
  </#if>
</#function>

<#function getRoundName round>
  <#assign roundName = round.type.getName()>
  <#if roundName?contains("$round") >
    <#local roundLocalized><@spring.message "round" /></#local>
    <#assign roundName = roundLocalized + " " + round.orderNumber>
    <#return roundName>
  <#else>
    <#return roundName>
  </#if>
</#function>

<#function activeRound round>
  <#if round == fixtures[0].round >
    <#return 'active'>
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
  <link rel="stylesheet" href="/css/style.css">
  <link rel="stylesheet" href="/css/predictions.css">
</head>

<body>

<section>
  <h1>${fixtures[0].round.season.competition.name}</h1>
  <div class="scroll">
  <#list rounds as round>
    <button class="${activeRound(round)!}" onclick="location.href='${baseUrl + round.orderNumber}'">${getRoundName(round)}</button>
  </#list>
  </div>
</section>

<section>
<#list fixtures as match>
  <div id="${match.id}" class="match ${matchDouble(match)!}" onclick="setDouble(this)" data-started=${matchStarted(match)}>

    <span>${getStartTime(match)}</span>

    <div class="row">

      <div class="column" style="padding-right: 8px">
        <img src="${match.homeTeam.logoUrl}" alt style="width: 50px">
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
        <img src="${match.awayTeam.logoUrl}" alt style="width: 50px">
      </div>

    </div>
  </div>
</#list>
</section>

<script type="application/javascript">
  window.Telegram.WebApp.ready();
  init();
  formatPagination();

  function init() {
    window.Telegram.WebApp.enableClosingConfirmation();

    const mainButton = window.Telegram.WebApp.MainButton;
    mainButton.onClick(function () {
      save();
    });
    mainButton.show();

    if (document.querySelector(".double")) {
      mainButton.enable();
      mainButton.setText("<@spring.message "buttons.save" />")
    } else {
      mainButton.disable();
      mainButton.setText("<@spring.message "buttons.save.disabled" />")
      mainButton.color = '#d7d7d7'
    }
  }

  function formatPagination() {
    let maxWidth = 0;
    document.querySelectorAll(".scroll button").forEach(roundButton => {
      let width = parseFloat(roundButton.offsetWidth) + 1;
      if (width > maxWidth) {
        maxWidth = width;
      }
    })

    document.querySelectorAll(".scroll button").forEach(roundButton => {
      roundButton.style.width = maxWidth +'px';
    })

    document.querySelector(".active").scrollIntoView({
      inline: "center",
      block: "nearest"
    });
  }

  function save() {
    const data = [];
    document.querySelectorAll(".match").forEach((match) => {
      if (match.dataset.started !== 'true') {
        let matchData = {
          "matchId": match.id,
          "predictionHome": match.querySelector('#scoreHome').innerHTML,
          "predictionAway": match.querySelector('#scoreAway').innerHTML,
          "doubleUp": match.classList.contains('double')
        }
        data.push(matchData);
      }
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
    window.Telegram.WebApp.MainButton.setText("<@spring.message "buttons.save" />")
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