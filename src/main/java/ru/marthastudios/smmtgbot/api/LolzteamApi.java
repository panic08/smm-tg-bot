package ru.marthastudios.smmtgbot.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.marthastudios.smmtgbot.api.payload.LolzteamPaymentResponse;
import ru.marthastudios.smmtgbot.property.LolzteamProperty;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@Component
@RequiredArgsConstructor
@Slf4j
public class LolzteamApi {
    private static final String LOLZTEAM_API_URL = "https://api.lzt.market";
    public final String PAYMENT_URL_FORMAT = "https://lzt.market/balance/transfer?username=%s&comment=%s&amount=%f&currency=usd&transfer_hold=false";
    private final LolzteamProperty lolzteamProperty;
    private final RestTemplate restTemplate;

    public LolzteamPaymentResponse getAllPaymentByStartDayDateAndEndYearAheadDate(int startDayDate, int endYearAheadDate){
        Calendar calendar = Calendar.getInstance();

        calendar.add(Calendar.DAY_OF_MONTH, startDayDate);
        Date fourDaysAgo = calendar.getTime();

        calendar.add(Calendar.YEAR, endYearAheadDate);
        Date oneYearAhead = calendar.getTime();

        // Форматирование дат в RFC 3339
        String rfc3339Format = "yyyy-MM-dd'T'HH:mm:ssXXX";
        SimpleDateFormat sdf = new SimpleDateFormat(rfc3339Format);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        String fourDaysAgoString = sdf.format(fourDaysAgo);
        String oneYearAheadString = sdf.format(oneYearAhead);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + lolzteamProperty.getToken());

        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = LOLZTEAM_API_URL + "/user/" + lolzteamProperty.getUserId() + "/payments?type=receiving_money&is_hold=false&startDate=" + fourDaysAgoString
                + "&endDate=" + oneYearAheadString;


        LolzteamPaymentResponse lolzteamPaymentResponse = null;

        try {
            lolzteamPaymentResponse = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    LolzteamPaymentResponse.class
            ).getBody();
        } catch (RestClientException e){
            lolzteamPaymentResponse = new LolzteamPaymentResponse();

            log.warn(e.getMessage());
        }


        return lolzteamPaymentResponse;
    }
}
