$(document).ready(function(){

    $('#requestDataParsingResultDiv').hide();
    $('#sendRequestButton').prop('disabled', true)
    $('#certRequestInputText').bind('input propertychange', function() {
        $.ajax({
            url: "processCertReqData",
            data: {certRequestInputText: $("#certRequestInputText").val()},
            success: function (result) {
                handleRequestDataParserResult(result);
            }
        });
    });

});

/**
 *   String cn;
 *   String o;
 *   String ou;
 *   String c;
 *   String orgId;
 *   String errorMessage;
 *
 * @param result
 */
function handleRequestDataParserResult(result) {
    if (result === null || result === undefined){
        $('#requestDataParsingResultDiv').html("Server connection error");
        $('#requestDataParsingResultDiv').show();
        return;
    }
    let jsonResult = JSON.parse(result);
    let errorMessage = jsonResult.errorMessage;

    if (errorMessage != null){
        if (errorMessage === "empty"){
            $('#requestDataParsingResultDiv').empty();
            $('#requestDataParsingResultDiv').hide();
        } else {
            $('#requestDataParsingResultDiv').html(errorMessage);
            $('#requestDataParsingResultDiv').show();
        }
        $('#sendRequestButton').prop('disabled', true)
    } else {
        $('#requestDataParsingResultDiv').empty();
        $('#requestDataParsingResultDiv').hide();
        $('#sendRequestButton').prop('disabled', false)
    }

    renderName(jsonResult.cn, "commonNameInput");
    renderName(jsonResult.o, "organizationNameInput");
    renderName(jsonResult.ou, "orgUnitNameInput");
    renderName(jsonResult.orgId, "orgIdentifierInput");
    renderName(jsonResult.c, "countryInput");

    function renderName(data, id) {
        if (data === null || data === undefined){
            $("#"+id).attr('value','');
            return;
        }
        $("#"+id).attr('value',data);
    }
}


function strHasValue(strVal) {
    return strVal != null && strVal.length > 0;
}

function sendRequest() {

    let confirmDiv = $('<div>').addClass('cert-req-confirm-div');

    let attrTable = $('<table>').addClass('table table-striped table-sm');
    let cn = appendRow(attrTable, 'Common name', 'commonNameInput');
    appendRow(attrTable, 'Organization name', 'organizationNameInput');
    appendRow(attrTable, 'Organizational unit name', 'orgUnitNameInput');
    appendRow(attrTable, 'Organization identifier', 'orgIdentifierInput');
    let country = appendRow(attrTable, 'Country', 'countryInput');


    let countryRegex = /^[A-Z]{2}$/g
    let cnHasContent = strHasValue(cn);
    let countryHasValidContent = countryRegex.test(country);

    if (cnHasContent && countryHasValidContent){
        confirmDiv.append($('<h5>').addClass('main-color-text').html('Issue certificate to:'));
        confirmDiv.append(attrTable);

        bootbox.confirm(confirmDiv, function (e){
            if (e){
                $('#cert-req-form').submit();
            }
        });
    } else {
        confirmDiv.append('Attributes "Common name" and "Country" must have values and "Country" must have 2 characters (ISO 3166 country code)');
        bootbox.alert(confirmDiv, function (){});
    }


    function appendRow(attrTable, attrName, id) {
        let val = $('#'+id).val();
        if (strHasValue(val)){
            attrTable.append($('<tr>')
                .append($('<td>').addClass('cert-req-attr-name').html(attrName))
                .append($('<td>').html(val)));
        }
        return val;
    }

}

