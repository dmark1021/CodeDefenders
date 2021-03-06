var highlightLine = function (lines, color, superDiv) {
    if (!superDiv) {
        superDiv = "#cut-div";
    }
    var allLines = [];
    $(superDiv + ' .CodeMirror-linenumber').each(function (i, e) {
        var line = parseInt(e.innerHTML);
        allLines[line] = $(e).parent("div").parent("div")[0];
    });
    for (var l in lines) {
        $(allLines[lines[l][0]]).css('background-color', 'rgba(' + color.r + ',' + color.g + ',' + color.b + ',' + lines[l][1] + ')');
        //$(allLines[lines[l][0]]).css('opacity', lines[l][1]);
    }
};
var MUTANT_SHOW_LIMIT = 5;
var COVERED_COLOR = [];
COVERED_COLOR.r = 153;
COVERED_COLOR.g = 255;
COVERED_COLOR.b = 153;
var UNCOVERED_COLOR = [];
UNCOVERED_COLOR.r = 255;
UNCOVERED_COLOR.g = 153;
UNCOVERED_COLOR.b = 153;

var MutantStatus = [];
MutantStatus.UNKNOWN = -1;
MutantStatus.ALIVE = 0;
MutantStatus.FLAGGED_EQUIVALENT = 1;
MutantStatus.KILLED = 2;
MutantStatus.EQUIVALENT = 3;

var lineContent = [];

var lineSummary = [];

var timeoutFunction = null;
var lastLine = 0;
var mutants = [];
var preparedMutants = [];

var getMutantsFunc = function(){return [];};


$(document).ready(function(){
    // getMutants may be defined in game_highlighting.jsp if we're on a page
    // which uses it
    if (typeof(getMutants) === typeof(Function)){
        getMutantsFunc = getMutants;
    }
    mutants = getMutantsFunc();
    prepareMutants();
});

var prepareMutants = function(){
  for (m in mutants){
      var mutant = mutants[m];

      mutant.status = MutantStatus.UNKNOWN;
      if (mutant.equivalent == "PENDING_TEST"){
          mutant.status = MutantStatus.FLAGGED_EQUIVALENT;
      } else if (mutant.equivalent == "DECLARED_YES" ||
          mutant.equivalent == "ASSUMED_YES"){
          mutant.status = MutantStatus.EQUIVALENT;
      } else if (mutant.alive){
          mutant.status = MutantStatus.ALIVE;
      } else {
          mutant.status = MutantStatus.KILLED;
      }

      for (l in mutant.lines){
          line = mutant.lines[l];
          if (!preparedMutants[line]){
              preparedMutants[line] = [];
          }
          preparedMutants[line].push(mutant);
      }
  }
};

var createAction = function(label, classType, contents){
// return '<span class="action"><a onmouseover="' +
//     ' $(\'#mutationPopup\').find(\'.mutationInfoExpanded\').show();' +
//     '$(\'#mutationPopup\').find(\'.mutationInfoExpanded\').html(\'' +
//     contents.split("\"").join("\\'") +
//     '\');">' +
//     '<span class="mutantCUTIcon ' + classType + '">' +
//         '<span></span>' +
//     '</span>' +
//     '<span>' + label + '</span>' +
//     '</a></span>';
    return '';
};

var prepareMutantDetail = function(mutant){
    return '<p><span class="left">' + mutant.playerName + '</span>' +
        '<span class="central">' + mutant.id + '</span>' +
        '<span class="right">' + mutant.score + '</span></p>';
};

var prepareMutantHeading = function(title){
    return '<h5>' + title + '</h5><p><span class="left header">Creator</span>' +
        '<span class="central header">Mutant ID</span>' +
        '<span class="right header">Points</span></p>';
};

var createPopupFunction = function(mutantType){
    return function(){
        var t = $(this);
        var parent = t.parent();
        parent.mouseenter();
        $('#mutationPopup').find('.mutationInfoExpanded').html(lineSummary[lastLine][mutantType]);
    };
}

var mutantLine = function (superDiv, showEquivalenceButton) {
    if (!superDiv) {
        superDiv = "#cut-div";
    }
    var allLines = [];
    $(superDiv + ' .CodeMirror-linenumber').each(function (i, e) {
        var line = parseInt(e.innerHTML);
        allLines[line] = $(e).parent("div").parent("div")[0];
    });

    $(".codedef-line-mutant").remove();

    for (var l in preparedMutants) {
        const lineNum = l;
        const id = "mutantLine" + lineNum;

        const divId = " #" + id;

        var aliveMutants = 0;
        var equivalentMutants = 0;
        var flaggedMutants = 0;
        var killedMutants = 0;

        var mutantDescriptions = [];

        mutantDescriptions['alive'] = prepareMutantHeading("Mutants Alive");
        mutantDescriptions['equiv'] = prepareMutantHeading("Mutants Equivalent");
        mutantDescriptions['flagged'] = prepareMutantHeading("Mutants Flagged");
        mutantDescriptions['killed'] = prepareMutantHeading("Mutants Killed");

        // sort mutants by score
        preparedMutants[l].sort(function(a, b){
            return b.score - a.score;
        });

        for (var m in preparedMutants[l]) {
            var mutant = preparedMutants[l][m];
            var detail = prepareMutantDetail(mutant);
            if (mutant.status == MutantStatus.ALIVE){
                if (aliveMutants < MUTANT_SHOW_LIMIT) {
                    mutantDescriptions['alive'] += detail;
                }

                aliveMutants++;
            } else if (mutant.status == MutantStatus.EQUIVALENT){
                if (equivalentMutants < MUTANT_SHOW_LIMIT) {
                    mutantDescriptions['equiv'] += detail;
                }

                equivalentMutants++;
            } else if (mutant.status == MutantStatus.FLAGGED_EQUIVALENT){
                if (flaggedMutants < MUTANT_SHOW_LIMIT) {
                    mutantDescriptions['flagged'] += detail;
                }

                flaggedMutants++;
            } else { // it's impossible for status to be unknown
                if (killedMutants < MUTANT_SHOW_LIMIT) {
                    mutantDescriptions['killed'] += detail;
                }
                killedMutants++;
            }

        }

        if (aliveMutants > MUTANT_SHOW_LIMIT){
            mutantDescriptions['alive'] += "<p>+" + (aliveMutants - MUTANT_SHOW_LIMIT) + " More</p>";
        }
        if (flaggedMutants > MUTANT_SHOW_LIMIT){
            mutantDescriptions['flagged'] += "<p>+" + (flaggedMutants - MUTANT_SHOW_LIMIT) + " More</p>";
        }
        if (equivalentMutants > MUTANT_SHOW_LIMIT){
            mutantDescriptions['equiv'] += "<p>+" + (equivalentMutants - MUTANT_SHOW_LIMIT) + " More</p>";
        }
        if (killedMutants > MUTANT_SHOW_LIMIT){
            mutantDescriptions['killed'] += "<p>+" + (killedMutants - MUTANT_SHOW_LIMIT) + " More</p>";
        }

        var icon = '<div id="' + id + '" style="height: 20px; margin-left: 5px; float: left; margin-right: -25px; position: relative; z-index:2000;" class="codedef-line-mutant">';

        if (killedMutants > 0){
            icon += '<span class="mutantCUTImage mutantImageKilled"><span>' + killedMutants + '</span></span>';
            // icon += '<img src="images/mutantKilled.png" alt="\' + quant + \' mutants on line \' + lineNum + \'" width="20" />';
        }

        if (equivalentMutants > 0){
            icon += '<span class="mutantCUTImage mutantImageEquiv"><span>' + equivalentMutants + '</span></span>';
        }

        if (flaggedMutants > 0){
            icon += '<span class="mutantCUTImage mutantImageFlagged"><span>' + flaggedMutants + '</span></span>';
        }

        if (aliveMutants > 0){
            icon += '<span class="mutantCUTImage mutantImageAlive"><span>' + aliveMutants + '</span></span>';
        }

        icon += '</div>'

        $(allLines[lineNum]).before(icon);

        var content = '<span id="mutationPopup">'

        content += "<span" +
            " class='mutationInfoExpanded'>" +
            "<h5>Info</h5><p>Hover" +
            " over for info</p></span>"

        // if (aliveMutants > 0) {
        //     content += createAction("Alive Mutants", "mutantImageAlive", mutantDescriptions['alive'])
        // }
        //
        // if (flaggedMutants > 0) {
        //     content += createAction("Flagged Mutants", "mutantImageFlagged", mutantDescriptions['flagged'])
        // }
        //
        // if (equivalentMutants > 0) {
        //     content += createAction("Equivalent Mutants", "mutantImageEquiv", mutantDescriptions['equiv'])
        // }
        //
        // if (killedMutants > 0) {
        //     content += createAction("Killed Mutants", "mutantImageKilled", mutantDescriptions['killed'])
        // }
        if (showEquivalenceButton && aliveMutants > 0) {
            // TODO How do we get the contextPath ? it might not be necessary if we use relative href
            mutantDescriptions['alive'] += '<span class="action"><a href="javascript:' +
                ' if(window.confirm(\'Flag line ' + lineNum + ' as' +
                ' Equivalent?\')){window.location.href = \'multiplayer/play?equivLine=' + lineNum + '\';}">' +
                '<span class="mutantCUTIcon mutantImageFlagAction"><span></span>' +
                '</span> Claim Equivalence</a></span>';
        }
        content += '</span>';

        lineContent[lineNum] = content;

        lineSummary[lineNum] = mutantDescriptions;

        $(divId).hover(
            function () {
                // Setting the current div z-index stops mutant icons
                // appearing through the popup.
                $('.codedef-line-mutant').css("z-index", "2000")
                $(divId).css("z-index", "3000");
                if (lastLine != lineNum) {
                    drawMutants(lineNum, this);
                    lastLine = lineNum;
                }
                clearTimeout(timeoutFunction);
            }, function () {
                timeoutFunction = setTimeout(function () {
                    $('#mutationPopup').fadeOut(150);
                    lastLine = 0;
                }, 2000);
            }
        );
    }

    $(".mutantCUTImage.mutantImageAlive").hover(createPopupFunction('alive'), function(){});
    $(".mutantCUTImage.mutantImageKilled").hover(createPopupFunction('killed'), function(){});
    $(".mutantCUTImage.mutantImageEquiv").hover(createPopupFunction('equiv'), function(){});
    $(".mutantCUTImage.mutantImageFlagged").hover(createPopupFunction('flagged'), function(){});
};

var drawMutants = function (lineNum, ele) {
    $('#mutationPopup').remove();
    var content = lineContent[lineNum];
    $(ele).append(content);
};