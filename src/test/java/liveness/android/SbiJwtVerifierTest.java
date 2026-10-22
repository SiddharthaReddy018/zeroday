package liveness.android;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Test PKI generated with openssl: ROOT signs DEVICE; ROGUE is an unrelated self-signed key (valid 2026-10-19 .. 2046-10-14). */
class SbiJwtVerifierTest {

    static final String ROOT_CERT = "MIIDFzCCAf+gAwIBAgIUGWlPk+DaD5IBMDuzRsUQICYSt+EwDQYJKoZIhvcNAQELBQAwGzEZMBcGA1UEAwwQVGVzdCBEZXZpY2UgUm9vdDAeFw0yNjEwMTkwNDAyMTRaFw00NjEwMTQwNDAyMTRaMBsxGTAXBgNVBAMMEFRlc3QgRGV2aWNlIFJvb3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCtpo0/k0OQsVNX/1/6PUB4QttHwod5E1s45KepOZsPQ2/qx6YpHiuI0DTQ43YmPmNn2zt8Zi9Yl2uAFGKGS0dYSGeB1MbY23pyYVjc/lvus4l2d4TW8C0ovyXJrpOBS0mm0XOxQHZ+NxSbwQ1bE3+B6zeJOQOwXzIJNi0kqVOkJLzhuacQsq0Ni0+Tuot5Nix+KQdYuALRSRPTph4UV4wlp+rltw3xvr/tkOYZgJ9oUrmMJxjfUN403dI/5PhnV2Vj7xvlcYY+Gr0KCm+9nCA/zeiauUW+8wfTtuAiJyTBoRTt8F6e6g2uo9l1vicV/7w3UXYek1weEKEW93MWTrCjAgMBAAGjUzBRMB0GA1UdDgQWBBQ1L+Of/7Wow8TU2W3XWwOXRbdUAzAfBgNVHSMEGDAWgBQ1L+Of/7Wow8TU2W3XWwOXRbdUAzAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCoSngEy8gkxpDYBqZfKf//oMqq55h8P/qppsKGWsOy4YseVeEWhRfMZhylR7AM+xRNY9/+I+c0mhNI20DcTqinDFkXjDHiKCUvAhIuup6+madU99cX0oJ45FxxRwdTTRyMiUu+qe0PzATt18dWb1swLfz0iSXyGoxR+av/za42AR3yiF9NapI9yuE+4OcxX/VldVQbPMUemS9OpPYy1ls6M3ROCvdkfRh750N2aytDUxKXyvgCZJxWS3zYKbhOJ9RZdVMj7/yj4DWBtZA6GS3Me/qLSW22wdjR931Yiw+/f0Le5fFqqaECU4qPvNMjzWQz6J1Nog2Yq9+aFyJsk2FI";
    static final String DEVICE_CERT = "MIICvDCCAaQCFDvldvZ2NwKPuFs9q5p5wQbQRDHQMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAMMEFRlc3QgRGV2aWNlIFJvb3QwHhcNMjYxMDE5MDQwMjE0WhcNNDYxMDE0MDQwMjE0WjAaMRgwFgYDVQQDDA9UZXN0IFNCSSBEZXZpY2UwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC24ohJvOl0oOFuW7dN/+0S+roQAz+ZSpwhHaaIId0aMWPiL8+VqBy8oQukLi8RBbTD8H+1WygWQ3AJ1r/1iQ9q4jZF9OCN5FwfD1P2retFQv7dsCiYzXgbSBmSZQU2/u6Kl5dJ3ugdpbipiLfKjRcghdhbTWg9QBTu6SMiL0CnLhHgdItQhaXrG63D18+5ZwAj42thlOjCj9NpIfFQBf55empbrUwzZTyUKUHtwdiGXLCIU1dQR4YpqL1di//EIS1M6PfqVLpD3UKUv2jUjfj3W2veFMrLD90ATbuek7JzhU4GPb5o8hhLUsDrYKhbZQ8LuC0pD+rTc9z/ERuk7v3dAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAD39lByzwbmzJsTPZbi89E8WiXkhnNRTvHmzzK3dlaDb03FZazjmTYBqnXgFaZKGr6FQsq4bGRSABy/YMf8MpbDQ+F7J5386GjheERY2sQtBB3Twe9ChZLQ4+bNYjScHM8pWuOliHXA9Z+6A5QORnL1nFOE9Vjm3w0MVAZryCYY7/rodBytOfpHJkuvqV2zanQVlIX0ISnvJc2Z5v/57VDGu+oFJpBJ9pqHlCvolvf+Ibqm9iaEn14ISPqvv7VuV8hbVQ5z4eZk2DxdTnK37u9EIWeTURdgx9XAFv2PD6pwy/uTDhbAcw6giMHNX35E4xnvB9O7jMAiwBo+7KXpC+IY=";
    static final String DEVICE_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC24ohJvOl0oOFuW7dN/+0S+roQAz+ZSpwhHaaIId0aMWPiL8+VqBy8oQukLi8RBbTD8H+1WygWQ3AJ1r/1iQ9q4jZF9OCN5FwfD1P2retFQv7dsCiYzXgbSBmSZQU2/u6Kl5dJ3ugdpbipiLfKjRcghdhbTWg9QBTu6SMiL0CnLhHgdItQhaXrG63D18+5ZwAj42thlOjCj9NpIfFQBf55empbrUwzZTyUKUHtwdiGXLCIU1dQR4YpqL1di//EIS1M6PfqVLpD3UKUv2jUjfj3W2veFMrLD90ATbuek7JzhU4GPb5o8hhLUsDrYKhbZQ8LuC0pD+rTc9z/ERuk7v3dAgMBAAECggEAGoekI6Y+64dHDH5LigodNwqeISKHgFI9ceReOxNGgr01cyNiZqBbgtWN3cRBWaPNRDtIG2Pc3dXsk9DOSe0XCndETkOmq467+ll74aX7IjIEzN34FaqAyNxdsUnsRr0Wl45ECRhH7b0Tm6xUlRKAj1w7NYt2GqSIJNw3F4dpRpb+gmsYMBcJAf+oM54x/y1CB9yeOiWQE7rB6U7qVHuszloE7EZPDRh9THOR1hyVnIVNBeW7wR6s3cYwPBg3sCsYhw4eEHbiKw/FczGWexAkLtn/dRXnwpGF00VpZc9nWVrlfpFsu8q4Np7GY0/sh8kbquu/Fe9T7NxrWcktnAkqNQKBgQDbV7IMvNwM0HnensMAFc/x+szbZNvzUGqdRFUErVIP4bOHQSEofIgTz03kzjUevO6I58/7UGmK9CMB0AvbgAgyEUJ9r9lfa1/keNmbOPrkuGgTUAmgMtfPNfE8CetH88UYxafby46V45LRfSmZu39rWDXPWRLDq0pYiT09wGPfQwKBgQDVcwo2imubtxAnHBOp4GmYl3k49rkrDPusB9up2DUtJRIB7YSy4Ih9DcDVGA1yX0jKqveyFzqIFPBI3FvuE9KFLb0DOFQ9My7p+LPOuJzR+joFuTnBUngTsXJtIR4BFZGnhSwNSgJGId+FYzhhfQkeI9AUQUntVqE7RAY8zngMXwKBgQC3dScADmrC8RAtyn5xMM1HqKz1h+uMHCPAPGobylfL3syJAUmZkmdCom6VRIAboIymx7Umj4Bnb4FkP73bLsAsGFGTzKQ0chu3ffkMImF/MloiZRxX+I6WyphlzioTeWnOKWwXo68z4S9otpY39iJCB3qiXe8a58b8PHWTk7o0/wKBgCtr36QAvKtMrkiGeamRMWoNRmfEKR8M4RW584G1rfulEui5fMk2zA1EgvnWjsW5hIFWDlV4TDvqpobr7PedqLHXACBrkRdyFhUattlSzPQaRztq1sjxEeRPsMtqdUQbMyo0+SUq7RN/SI1GBe4vFW/AMVAth/B0MxBvYiV0iw9ZAoGATRxgIZL+ne+xSqQOzcK6ZrUCOgO6yHTP1BuHuuos+cEXRDQ8ZeenT7u6wUfSZ2Xx+SJNHElnXFi/GjrqLJm4XW2XHjrdv9AVTz18RzmPwzuYCAPaYjOzWTeneHWwgLyK1ESxYIpsvozuOpJUiJSdCyo+Z1YfsYF3D8o5R9qQ6v8=";
    static final String ROGUE_CERT = "MIIDATCCAemgAwIBAgIUfkxRnKsxLxsnYk5IY2cIMbwGMpwwDQYJKoZIhvcNAQELBQAwEDEOMAwGA1UEAwwFUm9ndWUwHhcNMjYxMDE5MDQwMjE1WhcNNDYxMDE0MDQwMjE1WjAQMQ4wDAYDVQQDDAVSb2d1ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAPm3hyXs4EWOYxgJF4YRhnKTMdL7cAfz3GgIo/b3a3hk1YQo5EelqgkfefmdXCPztYgTpW7TRbx4jvxt6pQAl9XnNYBSBiFn6AmuQKaHX1gkGLfaPbtdcJgcy1PKMLQ3FLh/n7AwtlZb9ybX41sncvAgW0MKcOIlH4DG48Rgt78zzxe7Uzo0InJjzrac7/IqDBowfLyEBbgRXKTHvyn2mr6J275B7LtRd3G6VzLCghJMBvKgoXtTeRSLRNhrss67cfAxYq21HFGJ8vCDgzBUVly8QDL1BDfbK5xVCmPeBPq/9r8UDQclbKgZLNx5f/wc9zx1/B933JN/OWW6xoK1A+MCAwEAAaNTMFEwHQYDVR0OBBYEFMnTBNpUzrydQyAd/imKaewZ7usnMB8GA1UdIwQYMBaAFMnTBNpUzrydQyAd/imKaewZ7usnMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBADJVdz6dQs2nNv9S3sVIx3Tf1uBrs0wtA4xOIqlBhsWpzrFlGSZZoPIi3G3P7vm9NOC/M/Z+TDzpZqxzuXkrpHot4vB968FeQD8jyyVUMzqzdCCzU4vcIMUqajKWa5tYBKyt6UjQecoY+5cPXZP494299bgDYJAh3FWJcsaVrpDbfku0heJs0dBj6mqbu+YDw3heGWGjYXpY+8slNaaFbNbdWhoL2vskPffjOU1PHEwgZV8cx4f2/LkeX7SQXjw74ZKcIOe4/SMen3Er+4OsFHgUXocZ5LiIHa4bBnMll5HBUEK/VSf0Bm2O+eCOJtQ47sVwMBswF8/P+EzXR4DmoSg=";
    static final String ROGUE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD5t4cl7OBFjmMYCReGEYZykzHS+3AH89xoCKP292t4ZNWEKORHpaoJH3n5nVwj87WIE6Vu00W8eI78beqUAJfV5zWAUgYhZ+gJrkCmh19YJBi32j27XXCYHMtTyjC0NxS4f5+wMLZWW/cm1+NbJ3LwIFtDCnDiJR+AxuPEYLe/M88Xu1M6NCJyY862nO/yKgwaMHy8hAW4EVykx78p9pq+idu+Qey7UXdxulcywoISTAbyoKF7U3kUi0TYa7LOu3HwMWKttRxRifLwg4MwVFZcvEAy9QQ32yucVQpj3gT6v/a/FA0HJWyoGSzceX/8HPc8dfwfd9yTfzllusaCtQPjAgMBAAECggEAZ2l9alNCPQvONbHuL/Mp/mC+jWTa7DAs4lhRroCf3OfFFEnq31DXrZd/Z8Om2KygTI32QIL1qisy646JSmlEd6ycF1vgdg0Vpoym8U+2SEYKhni2f9UeP6ANA9IDgV4eEpmng8todzL2kvtsh986Vf7QTF8SiFRfS3ER+c0jyuXUPtX4Rrf/5TaxFOvgI2KbWGGCAJ8spxZNWfXzvPQQdcEGCs2Tykpe+HAEXkEyq85xazWWzHUAIv769+3opmO01aEjnsvbzlYjSlaV9HJjp1rsPfPebiHFpC8i/Km3p8jBrCx4P7ohIRrHrRb4ycpi+yyBWS62e4ZYGtr8jZPs4QKBgQD+FDNUAa8ttlI4/+/K5bwpoBafc9gyV3Kn3AecnaUoX4vmoF0qiexoY6r7DP9vAvy/OBuP2o4Aj9WnjyGhUz0m0t8EqmW7SOwRZ4Bqtrh6rVt8rRFXKtqgBK+tZzhEMmI3ZVUBnIk9U4/2HYRfX3FAcKY1YO9MbylzFHrZaY3B1QKBgQD7muJejwhFPViSUk4ERJVwIEHWBQJ66pJdP1foaoOAY7tnNvzhqUf+naIfh/81MjzH4f66544nDwl4ZXcwMSlLXbVvaD5Wj8k3vpJHfwBB6u4Hni/GPFoLLYDZHxatQw+rdTs/q8kdE3O86oLTG1bQSX79qeT3umsYhTZ/yc1S1wKBgQD23i/FFuHbu4OGaH7YGIHLvAVfJpLQzxDTMkGfFblMUhG/ot8DHwG5p+WQil+BwFhNfzmge01wFFEWvkaB1jvbhhY1FU9FOqRMgNFQXns+j0QODqaDmFrWEdeT12/fK0ks4+NFbpAWKyP8kU91z2ldoFaOFMJN9NBERYjbx+h5hQKBgQDmBIDyxXxgIGc6zbe5OyILMdI+YLIVwSEx0lkBh27VD1giUij5/JJu+ZlfjisIbPcYHUmNgWd52ctkoA5IpokD95WJVhm3dRgVuElux3Jq0OXvXPR8HGyNT3o+BKfsmiRXMYOK//eKd2AV06bqbFH/dVF9gbZrq6eA8TgTt1/YXQKBgES1NqJYBHzCcvF8jtmm/Yvts155SB/yAFJV5I2/8VrCSmVHuf+DeX6fSDyuzohuxig21NYbU59K0MOD0em/3mWWeadr25G7j5YeZl+1n3H8N0bkuWbUfHrabni/2urjWmlM9OHrmT5R8pajzvwj1+iaElDVPOZLOROAnUfhLGg7";

    // Multi-hop PKI: Root -> Intermediate -> Leaf (valid 2026-10-21 .. 2046-10-16)
    static final String CHAIN_ROOT_CERT = "MIIDHzCCAgegAwIBAgIUKT6i6EJuz4a7N4o0MD+TEmVEwIowDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMVGVzdCBSb290IENBMB4XDTI2MTAyMTIwNDUyOVoXDTQ2MTAxNjIwNDUyOVowFzEVMBMGA1UEAwwMVGVzdCBSb290IENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuoLPMJY5Fprdm6pJG54wGbY3e1+o5o/aTBnY0j3OVTMiKof6gQPQTFfGx+Dk0fpHS5DnJuNTdk7tGxhJ1QbqFWUWrCNOTwEQawsLTc03EG1xiVI6+SQveNkZu3uDCDVHb7zU1pjsa0i6obtfT717rqXSB6l5z8cVEkAR5VASMOVDVjU9eMM1G2EFk2JKd6a4QecbFOVnHBPvLloOgYnJ2OL1TG4hFRnpsjYjq6bN6qhJaMdgs5uUmK5eHGgCgmptkNoayu3wQRkWHeu28H0YxbgWIfK3SJLNLCwV/KPUFPIx2M9pRg5NkVkHU4XR6IidhkIObD5SvpTU0PJ50MJxJwIDAQABo2MwYTAdBgNVHQ4EFgQUn58imNkxbL8TJnixtPjg6OZsDRUwHwYDVR0jBBgwFoAUn58imNkxbL8TJnixtPjg6OZsDRUwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwDQYJKoZIhvcNAQELBQADggEBAJFcRlBKEK+9W3imjNGgWgPIwgzGEkuELLFqLprLZ+fBTw453RRafqoYhesE/qVi6/3FngKdZ/a6p3H23EltZyNEI7BGXxcgD9+JT6CUQwo+RAQgGZKF09WIUXubPdqxIlk4r8AuPYPGL0+S9ASBZqBdFI41ZxURwWFcUgPq3eO/8/fMUJw79gedreURi1ZaqxiMeoP9/aZEx6LvP2gl/Va9DZxQ2VDEMb8OCXheJtsAPi8WqkZEETfL59a8YXmSe/RXveCgpu7fHc+sxHZtY9wnneW/XUihjE+Egh04ntBXlTNBuut+k/6WrffYJ0k9B/GDdpACd3EPo1YWpmr6m8k=";
    static final String CHAIN_INTER_CERT = "MIIDJzCCAg+gAwIBAgIUTsxTX2Dv1Yib66cKbFaWn4XvkVowDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMVGVzdCBSb290IENBMB4XDTI2MTAyMTIwNDUyOVoXDTQ2MTAxNjIwNDUyOVowHzEdMBsGA1UEAwwUVGVzdCBJbnRlcm1lZGlhdGUgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC9vNQjYLHZzhe9ig0oboLpJnjE3SxgplDbdS1mu7eHo5ATp1WfVAqgcsnti/MVQ6rtkDKkdYzUbAYEBpaK9Xc4zADjSbu793980XOxhWZt4pXtNOd2Fil3ffkpprqhPlzVkM8S6nuA7nl8Bgaicq8V24+CrRvgp2XJ6oMy7Q2r1hlwR0guqkxHwzsemFOib7cBQZyaLhj/V7306IXCMbBsbK7/3cm0fHiKx9Qc+TR+PNlh6s6jHGpLxqlpXK1tQKf3QSvgQBfy/78yjrvOKRIXz0sVPnefAvSw1JsPZ2fYthER9RJr+hXhn46UQyM8HZBKIjFbey8RSCH6qBRdiFLrAgMBAAGjYzBhMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBS/1t56fsIArrd9AQUK1J6/oV2wfzAfBgNVHSMEGDAWgBSfnyKY2TFsvxMmeLG0+ODo5mwNFTANBgkqhkiG9w0BAQsFAAOCAQEAtN0lEPVvxLTx/WQU+dbj6PdS9RfiJYgWvcwBYaeQ2F79ni2R0A5GOKmJf2SVj44+rHeTo/YOytfayaujxusJau6Loan51uD7T60DCigaZmMnMr35qlSn/aN8GIDnmLGHyIs2G4TMXbwxlDsGhguycNP0P/kscuEVRNgY2WcgTJAEiOIOmQozlOzcGNHrb/59cswKHShHYpZgORaw7RsCTcUJLT46cCMe6zhSHgOH99fHtGATHxfHTQIE5N8CCxzlwi/39oG15qqhSB0QRy9eWUN2hbnxVZFlu5YvomP2u/jGabMLC9g8L4zL2Z9CrpE7RkhktHsu03xUlCGhwghWdA==";
    static final String CHAIN_LEAF_CERT = "MIIDITCCAgmgAwIBAgIURgTPmy8zvtBI5ofApHAOWDEpkfAwDQYJKoZIhvcNAQELBQAwHzEdMBsGA1UEAwwUVGVzdCBJbnRlcm1lZGlhdGUgQ0EwHhcNMjYxMDIxMjA0NTI5WhcNNDYxMDE2MjA0NTI5WjAUMRIwEAYDVQQDDAlUZXN0IExlYWYwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDeJFFChSm7D3D5kIK2yjcinrpOjt7Y66VVEm5Xs3lH+yYASUeC8g2NgNygehYHd1cGmA4RDr+dXM0O12bf3TADmZsnCVV0aQsU1SHd4srF0kR5cBm+xdivJK628pE1OxgNFAicbUoWOfWpBBfio5E31AsEn7P1XxVwpNWcnBvaMx8yuDO4qbohO90mtf0Z+5kg40ZKx8lcHG3UQ66BBYguik9GVSbJyqrgqAoO6bhq9k6ezVlO480xVtCKy9fXWWDzjE7GH7yWWwqOk4TyRsdsOfPrOsFJJ/zZ32njuc7czI+U1i93GzefArL/Wk6NGV8E7NpIIoPOOOZ+YMJL8Kh7AgMBAAGjYDBeMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMB0GA1UdDgQWBBSmVDtPZlXW7rFhofqLM3nUT6rm7DAfBgNVHSMEGDAWgBS/1t56fsIArrd9AQUK1J6/oV2wfzANBgkqhkiG9w0BAQsFAAOCAQEAZYz5Fi1bjj+cknQQBYG89hf0HpydpHx+a7tq4qstELoA7NLooOG9kgjTWSwlgEQk0KjliLcxzIWzKqK1+JZvH8Djka35hG+Gy6GFotC16PheQ1hp5PRcjcbLfSmyfFar2LSOyFT+B4tAPnkJo8SvkpDt2DeJbYQTTp0U4475zeLvWNHvIxeT66+SuiUXcq6z+xtmsCgovfVDV6GLFu+9JHgEYPyR3hSjeoevsR5iaAItRIs4gUG/oxukaagi0/F1foYMZVGSa2svpyyRkybCZuwKvbEgVBccnoRdBarlkZ+t7mGW45RecfGisZAqN9PfyNxuLggmbbndiegFvUK7lA==";
    static final String CHAIN_LEAF_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDeJFFChSm7D3D5kIK2yjcinrpOjt7Y66VVEm5Xs3lH+yYASUeC8g2NgNygehYHd1cGmA4RDr+dXM0O12bf3TADmZsnCVV0aQsU1SHd4srF0kR5cBm+xdivJK628pE1OxgNFAicbUoWOfWpBBfio5E31AsEn7P1XxVwpNWcnBvaMx8yuDO4qbohO90mtf0Z+5kg40ZKx8lcHG3UQ66BBYguik9GVSbJyqrgqAoO6bhq9k6ezVlO480xVtCKy9fXWWDzjE7GH7yWWwqOk4TyRsdsOfPrOsFJJ/zZ32njuc7czI+U1i93GzefArL/Wk6NGV8E7NpIIoPOOOZ+YMJL8Kh7AgMBAAECggEACdvJwW6Aeh1G0cSQkIUr6jzqHsdna3AVz884F2tPJBsoxhV6d3y/jjqcUADrDlzE/3VrFmW1qkmujgyQM7owwdAk2xgmczqgM8X7sUm53wqkYFFRyy4jni4+FL9yI08ik54CfdK52X+r/9q/Jp9EuBoQfR2LUoXsQSoHkuI4uDe8a8znAGj+SP+LlLCDYhBVjVQNK/IjoVvA77VGxh6uVZ0cTKTUzAUod/I4xfhUlHRCsr+MPQJ2vsI/3NF6cFiHHIQvD1UBOQlRzfcjSC31GvOnEDslU4VMKB88nwBgVm5i9abxo6Jid2RHYWLnYzLa1HNBCkCTwgkfPDCqFTLckQKBgQD0kOow5WRQ9EOrDPZ53bAwJm8OnKgMpSuBL4E0cMWZPlYe2YMP7NvV6YdFN33SUCQj4hpW5Uu5BsPiDA4uFCA4SMJV7Q7uYRyD3OTb0nCycMQ/RPazj7stTwirXinI3y2wBSU6KJvaFyKzErQunj3wgT5x+1Q8CSs92KPL1i4oiwKBgQDohwTR0NnhO5hs4Dha1Bs1yBlAQxxiwrOjU/wKxiSFEiO8yoxXIRAPRJxqKouUtQrWSH52xF3gZGb0vQcMI1d8Gq19125+5nbqZKv0TsXRTfQvnuXek3p7XD4k0lB9GIjKWHfw5hsxMH7kXDsG4gd7bRjo8AZNhmUrMciOnGeN0QKBgFHa+vdYn8XUq51hnlFVmLXU91DNsBVjZvc7HiSeJhTPX2W96Xb4EPA81FCfbRZqSl5UO/qfjkOkdwV+gXR8uff8qka2LEwu5qtf0J2ehqfCxHxkqamZzs0wabdpsdSQlisW3cnkc52AJtYGyZXc0uBfcbYWp6/cZcOyccg0rIhXAoGAeqfOKQcsBx0ymCvmTjMYb1QvzbAY5xehPoRsrRo75V3eMBX/1W4BVKxyfaYXnsepAcOXwfpvMpCDppGXiBWUbkh8P6dGB/Z1v8p+0g5DVtbx+bStuKy+rRfJSA8Qmgw5C9OC4TXgHiBgrwDfug+8tqSjwSvrhzVfZFuMWX2zOiECgYEAkzrO5DZncSnZXkbu7cLI2WwhANN/e7/n7YvVYEy3QC4JSimnmgnSMxngoTEvWCJkC1Kni2uo0oGFe9n2bOfM5tobP1VYtRMZ0TGEhfAvNuZPMoVCr1+hQMWJyUosztpp2uUWqTecThphQA5EMF/RHmiEFQPh3NBMO57adBARmyg=";

    // Wrong KeyUsage Leaf (only keyEncipherment, no digitalSignature)
    static final String WRONG_KU_CERT = "MIIDKDCCAhCgAwIBAgIUDpkY5GJwV8jrBcgGshoB2dktgb8wDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMVGVzdCBSb290IENBMB4XDTI2MTAyMTIwNDUyMloXDTQ2MTAxNjIwNDUyMlowIzEhMB8GA1UEAwwYVGVzdCBMZWFmIFdyb25nIEtleVVzYWdlMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnG5fZyePIrsSXqCcw+DT3944m6jGl/qhgsWtS5iQZvTenPe7YhW6kcPj1IYM8K1nAOMRwcE0ogcRRfFjvOG4cDjm0xhH5ChJNnUczzHnPfyWNb7fbxTqCG2AYTRTjqvT9jcDLZK23k810O9EuOVCT5F/y6Dwi/JPR8dijeXglm/YgR3efQHuYT8O0UNfZLVBXoGx8Kvt37z4C0s7ONPghBY3hxpaeqVD7Wp74AMjZ4a2AQdUO9xJjLSeco8/7s5AfrHDM3RKVIcTIkWun9u+RiyUpCLJQVzA0QP3w01Hmll63ZDJemS+15OJNDUdoyxhszUD1cYSeaT0W7Hb2CDx5QIDAQABo2AwXjAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIFIDAdBgNVHQ4EFgQUfSKMiG+lxRyB8KDebKF0FxFkreowHwYDVR0jBBgwFoAUHW2VlNtrvLO9sAWLvTZS9rmD7/YwDQYJKoZIhvcNAQELBQADggEBAJU3m55Q5ktMQ4GkhP01q6/TvZJmvqL6J1cBX2z4CUzero1GWRKsyIvLWkZxUNZRQkGw1MeeGrYYLK5FP1Hkyh6fcdBc2DhFcBWTEvyuSokXLnCZI7piV71167BLw2ITFXmme4GwkwpKlwxyA4ZcOjWcNHWAsQxg1YIp8xyR1ZFwYGlB8vdV/TFLtMk/tMYJy2rgaKf1LahuJ712psuUZsKk8IIO+mK/SdpysUOB2WKlpR+3oIhnMjCxCxfHcyK+djvttO0QlkylzkMwp2wkekmSI8Yt70+vB6iA4+nX6VaLB9xTXYc5FpK8QBEIiDvEC5aZFGykA0AUWJruCDRpLcg=";
    static final String WRONG_KU_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCcbl9nJ48iuxJeoJzD4NPf3jibqMaX+qGCxa1LmJBm9N6c97tiFbqRw+PUhgzwrWcA4xHBwTSiBxFF8WO84bhwOObTGEfkKEk2dRzPMec9/JY1vt9vFOoIbYBhNFOOq9P2NwMtkrbeTzXQ70S45UJPkX/LoPCL8k9Hx2KN5eCWb9iBHd59Ae5hPw7RQ19ktUFegbHwq+3fvPgLSzs40+CEFjeHGlp6pUPtanvgAyNnhrYBB1Q73EmMtJ5yjz/uzkB+scMzdEpUhxMiRa6f275GLJSkIslBXMDRA/fDTUeaWXrdkMl6ZL7Xk4k0NR2jLGGzNQPVxhJ5pPRbsdvYIPHlAgMBAAECggEAAuyjTEaL4U6AUOmNagdayU1vtHqVaoOXyl3krUxHAs24GFTJ7tL7f7xwQWmsy0EKnb8g9q95yupOkzFTN9WRFklSCLHkV3+99/kngTjBxcG3kyikJ6uglmi++ieOxP+HAQwqkIsKGnd+SOTNiEFXmdeH/T1fJyvv+dELPB4x3SNm9fwancTtB708oiUKmTAvzspsnJnZ/1jGhl3FYxx1MAqoLvpKOvwG+Z+eq3zSQXbWSsWgWFjiRC/VO01OusPHdcHFOSjxVxpF01WTyKKaikOCuH4tfDIaV24RWrTF8Q3Xp2s7ZsqOMdkLUj34ORUTMe0sSBqnf4zPY0qYmdmZ3wKBgQDY0yQQeIrgjZC+SrAs9hqXmWUaoM6iU1lq3q2nQEGlR19lhqIDRjtEl71jg3xZFS/quKc3GOxYPHatLSr7lPIrCCHItT9t48/RlBdS/oAZvTx2ZeZrecZlLUKDI8314z14mBGdYc+KVDqMDcvm3J1X0NXEC7VFvMvzkzF6xoOgFwKBgQC4sdPtvB/S8BPJdqh+eiQwTe0cIUan1BtH+1iAY5YsgZiUcAVN6zFGjjZiaYSmZFvt8iFnbJRdDD4QYGhnlDmTzSjLquwTjyEjweqz5xEUho8rBfHYUjVB7otY8w0YtStkmRneU5XwfB+qhQo32CchxigK5s9Ol8yDh9WecSrfYwKBgQCHypQHjLbNM1u3EWeMgQTedL8REizXFIQnnhX7bQ6vyCdCmjLVAiMULkP6EtrJn69GvSZcJ0u9ZwCcLHGC5iFUOd+Op5R16fq5Mpn47FKlzBIVxHOuU7Ggo9vH7aCLoilZYUOG4T7tTxrro6hTiU8xNCTshricWEUS4thWIAVHOQKBgGoyfew9doMAXjBCFSIcMwC/Xe4FyC2qDUG3aSzxliDjJ4t6WeJysLOkA+tURtyx6hYIWmKwkSJrbqhV9tJP7Udp+OIa1oFslB6OWti0rSKDOIEBewzqQAs4EVHE9yvUyC2gxroKj7VmeYK/2p8/n0iROucBzgOfzw12JkNjSMaHAoGAI2DEeeuMg9rKF2OY6k8hISCSWFvFFjzZGf+XCB/Q2T2eFxbvS8U5tdEc7Hp6uag9pXJF1CjVs3sJ+H5bSplLOiPsGOLKC4iHoMXnkx0Hk6rk7S6zVMtRc4S2u8V1/ndbiOZsCBEv8mXCUCWEI/qQ7wnRmLSId+HjYIHWUEq9nyo=";

    // Non-CA cert acting as intermediate (basicConstraints=CA:FALSE)
    static final String NON_CA_CERT = "MIIDITCCAgmgAwIBAgIUDpkY5GJwV8jrBcgGshoB2dktgcAwDQYJKoZIhvcNAQELBQAwFzEVMBMGA1UEAwwMVGVzdCBSb290IENBMB4XDTI2MTAyMTIwNDUyMloXDTQ2MTAxNjIwNDUyMlowHDEaMBgGA1UEAwwRVGVzdCBOb24tQ0EgSW50ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCiW1rEEEOdHFMT1gKJ29PXxU1BhLY0F/SQJMsdGU+0d6dHMK4Zc7jH/xmhS/1MOB9KaaflRfvECEeWJXW5JnxTLV6TOAWWIG9nT4ouzY3WmGEnE4HXzMZR+tNdwDLTmE+yL+R/nwwTEN4ydOifyVUw/9SbHoClR9CQJahmXjbS2wrDDWC4g/qAxvqggaoU2KjHMqPx90+9gZU6WHjs3rPD3zmzd261efquMxFL7MAnTYgFHldJpcOrRci/pmIXh3XncO/nJCwcpwywjbI1uv+VCJoWYne6Zme7M+xme8i1XtDQj3i9UUgsGj+Vg/9Znr/OlHmWFoI7oeUFM+bmqWrRAgMBAAGjYDBeMAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMB0GA1UdDgQWBBTJ7YdKdUpf68pnUH9NqsWmigbVMTAfBgNVHSMEGDAWgBQdbZWU22u8s72wBYu9NlL2uYPv9jANBgkqhkiG9w0BAQsFAAOCAQEAXqJX+QK3FEvlioN9NG8zrVJxh3AHeBdFAe5QuOHiNxFeJo82WPQWmQMC3yrqCL4Ef6H5jm6Ml/c7qo4nZyAMjEbtFl4Ez+BZCfYQmDu3uiiBGgpGShTvzAJRKehNQ4XsImD+wcPUvbzvuMHnhZ9wzZibONtce2ee4XScKR3MHfSofaZmYFR+Vp2olcHwpAdwJAHHukJRY+9i5QZnCUhkqVNzZhXgIggjgN02lPtf6dHSAO1SAlgftcdglxD2vYowt/5YJBDRYK+iKs6nhM291i74/lQlwogKLWyc5usz/aryq5ez7RFKWB/efy8dGyihkH75JcKj/WYDvp0kkRq+zw==";
    static final String NON_CA_CHILD_CERT = "MIIDJjCCAg6gAwIBAgIUU9euR+3dbxX07WgGpE4pMX0ZinAwDQYJKoZIhvcNAQELBQAwHDEaMBgGA1UEAwwRVGVzdCBOb24tQ0EgSW50ZXIwHhcNMjYxMDIxMjA0NTIyWhcNNDYxMDE2MjA0NTIyWjAcMRowGAYDVQQDDBFUZXN0IE5vbi1DQSBDaGlsZDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMNPxTxlNx+vB+LDuPb9YOXqOe9Hnro+aVJLByr14kxGweO6KPPuzPHn3DicnSGc/BRC3PbnrMOhdo0xj2TsMa4IshAtqXfgY1MU943OfWVpDQ653iUls7i9prOgoqCyzpxjPpDB/7h0adZaL3QNhmxxHcHktAK9F7WDaYAUWxJCJSR/SNDrOmrOxipZZoacyMvBqBJTHYcDKVm+CPr0450USjKMQbnj2+e6SjWk6hoXGOVja/0Xt7fksnkhV+yj5RkpcjpfKaoslNpvNlIuzEUGhXF+fSX0sFzoaF8d6b2Kd4W8AHD5wI+V3IUKh87nbud0lvN9t+rPvvMHHXtV5tECAwEAAaNgMF4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0OBBYEFKEkgDAUT2ERkDNAIluuqeZjGGvrMB8GA1UdIwQYMBaAFMnth0p1Sl/rymdQf02qxaaKBtUxMA0GCSqGSIb3DQEBCwUAA4IBAQCKQFA2KdgncAoqhIi3VhLFTHVIDOplJsN4GPbXFNDq/PLRjKE1+7yt5pGo5uQj90JXZpTo0Bpx51E1gJMfM+ZeIEfK09CViKUw08T/ihopwGGGroYXY+mJstThBeCcvnGMu+X0iRy/Tyw6ZIhAfhLmfNzXOmU4hkEX6Ul2euAYNuk6XKK2SOpt3wZQQcaf7g15+hOQudJ/0j3r/ARETAq7XxGjmeaJQVYdeWzjt4H/QrnjU0GWwukITUdTU4rACtK6qaRx1Muo4FlT0Tv2FWwMGtgPpio5Bo9F+60ukjfYO/dHJEbewoLDSkVo1ykmtV14OAenSnMpmiNW6FISok6Z";
    static final String NON_CA_CHILD_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDDT8U8ZTcfrwfiw7j2/WDl6jnvR566PmlSSwcq9eJMRsHjuijz7szx59w4nJ0hnPwUQtz256zDoXaNMY9k7DGuCLIQLal34GNTFPeNzn1laQ0Oud4lJbO4vaazoKKgss6cYz6Qwf+4dGnWWi90DYZscR3B5LQCvRe1g2mAFFsSQiUkf0jQ6zpqzsYqWWaGnMjLwagSUx2HAylZvgj69OOdFEoyjEG549vnuko1pOoaFxjlY2v9F7e35LJ5IVfso+UZKXI6XymqLJTabzZSLsxFBoVxfn0l9LBc6GhfHem9ineFvABw+cCPldyFCofO527ndJbzfbfqz77zBx17VebRAgMBAAECggEAC8ioPyAYfa49aqHhtBSgyTwZvk5uaEfPRagmew+RwzyDpzmDe73q4298/pCMP93a7RW78QzLcrjBYrNKSQS9I3PY/5Yr+bkWlQm9BioMaFcmTlE3zk090HOLrjBX/UCFq/2veAhCHCOwOHXKpic2dtMy5MdndmVorgFmqS9jgdUvGBLD/uEdjST1VUSuI823eksrZquLVCtcZASuNRujthwBbYq4NYAmkRn2nIkyxZiTwHExxItOcxAO5KHhJAkpuVHMC0b7TFVrX0ArnNZB4qavDWlEvB+TvqMFD3bS14xBdvgwkMnTQyaCu202Mtu1ceBj4MVv2otXAGFdyv2AfwKBgQD6HaGmAXOmgThUTppWLIgq4mfkHWSFJIg2KtJFaXKW7prpgpJsvRrkZ5ZRxQ3QAlje0tx8cBMUmHyz7GkXZ5p9TtDSlt+AlczG5jz8JWNcYilugpNPjPUvgbz0U60I6jAMbsf2Cv1EvI6BDmUIeu0DjMQU7uWtLp6OyEPNtMqNEwKBgQDH6BIknbyrCDqq90QbtfTBDqk64KztUZ3AD/SDRyzLYdKJQoufWz2oZBOxNU+9SYNE89/kuSeIx9S/YoIFYKBs+vLYuBJQMIrB9yFJhff3U3dmyvAD4dUqQwm7exbT/dnVHsENjJI2n90hCo5vnS5kr05QC0nQiDMtqQCq+wStCwKBgQDSU26m0Zu2Tf3Xb437n1EWdvOUcV8hU3ozysit1CGnaJjeeo21YUSnA7D1rDaUUBT5s+nzvq9c52oeR1SMePQCIfAa1wK198KPE/f+tVYbzvQ2WRmVkNxyCbCi6WaeE4k6O0Jt1gnq7oELZ9ZwTibXkF0nmUmM26I8QY47Aqg9/wKBgDYaV/ay/iHn20D0JOm/CBvL5P87WXHtOd2GM+3nsHbm5b0iVnltjzVoyG+YdxlKsQ1TV5AmkE2Tj7+wYHImIaT9EgaXYtVzHcq7ILYuXNsCKvmWCPl0AGYsuJCWEqqTNIZaiXMqTVQdVhKgMcaBatHzJCXDuFmI0xkTeUvNHgnbAoGAE3UJ6kHB9u/Nv9ea/IdknqDFWTqltzAGLp+asyUJ+1gG2YX7fdSqtZXMMeGbmFfNmRQtUpiXt347CiRuU+2gXwHIkDVrLmypE0ZcyDYfIaCQn9fwXTEJhzJPaU+172Q8BzNVizei03yVswX0YS3oqFTCZKipH73JA7v3pief5gk=";

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    private static SbiJwtVerifier verifier(String... anchors) throws Exception {
        java.util.List<java.security.cert.X509Certificate> l = new java.util.ArrayList<>();
        for (String a : anchors) l.add(SbiJwtVerifier.parseCertificate(Base64.getDecoder().decode(a)));
        return new SbiJwtVerifier(l, () -> NOW);
    }

    private static String sign(String certB64, String keyB64, String alg, String payloadJson) throws Exception {
        return signChain(Collections.singletonList(certB64), keyB64, alg, payloadJson);
    }

    private static String signChain(java.util.List<String> certChainB64, String keyB64, String alg, String payloadJson) throws Exception {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("alg", alg);
        h.put("typ", "JWT");
        h.put("x5c", certChainB64);
        String head = Base64.getUrlEncoder().withoutPadding().encodeToString(MiniJson.write(h).getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        PrivateKey k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyB64)));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k);
        s.update((head + "." + body).getBytes(StandardCharsets.US_ASCII));
        return head + "." + body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private static String reason(Exception e) { return e.getMessage(); }

    @Test
    void acceptsTokenSignedByDeviceCertIssuedByTrustedRoot() throws Exception {
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{\"a\":1}");
        assertEquals("{\"a\":1}", verifier(ROOT_CERT).verify(jwt));
    }

    @Test
    void acceptsTokenWhenTheLeafItselfIsPinned() throws Exception {
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{}");
        assertEquals("{}", verifier(DEVICE_CERT).verify(jwt));
    }

    @Test
    void fromPemLoadsRootCertificateProperly() throws Exception {
        String pem = "-----BEGIN CERTIFICATE-----\n" + ROOT_CERT + "\n-----END CERTIFICATE-----\n";
        SbiJwtVerifier v = SbiJwtVerifier.fromPem(pem);
        assertNotNull(v);
        assertFalse(v.isDisabled());
    }

    @Test
    void rejectsSelfSignedRogueCertificate() throws Exception {
        String jwt = sign(ROGUE_CERT, ROGUE_KEY, "RS256", "{}");
        assertEquals("UNTRUSTED_CERT", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(ROOT_CERT).verify(jwt))));
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{\"tx\":\"good\"}");
        String[] p = jwt.split("\\.");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"tx\":\"evil\"}".getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        assertEquals("BAD_SIGNATURE", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(ROOT_CERT).verify(forged))));
    }

    @Test
    void rejectsLeafCertWithKeyThatDoesNotMatchIt() throws Exception {
        // Header claims the trusted DEVICE cert but the token was signed with the ROGUE private key.
        String jwt = sign(DEVICE_CERT, ROGUE_KEY, "RS256", "{}");
        assertEquals("BAD_SIGNATURE", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(ROOT_CERT).verify(jwt))));
    }

    @Test
    void rejectsWrongAlgorithmIncludingNone() throws Exception {
        for (String alg : new String[]{"none", "HS256", "RS512"}) {
            String jwt = sign(DEVICE_CERT, DEVICE_KEY, alg, "{}");
            assertEquals("UNSUPPORTED_ALG", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(ROOT_CERT).verify(jwt))), alg);
        }
    }

    @Test
    void rejectsMalformedTokensAndMissingCertificate() throws Exception {
        SbiJwtVerifier v = verifier(ROOT_CERT);
        for (String bad : new String[]{null, "", "a.b", "a.b.c.d", "!!!.!!!.!!!"}) {
            assertEquals("MALFORMED", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> v.verify(bad))), String.valueOf(bad));
        }
        String noX5c = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + ".c2ln";
        assertEquals("NO_CERTIFICATE", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> v.verify(noX5c))));
    }

    @Test
    void enforcesCertificateValidityWindow() throws Exception {
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{}");
        java.security.cert.X509Certificate root = SbiJwtVerifier.parseCertificate(Base64.getDecoder().decode(ROOT_CERT));
        SbiJwtVerifier early = new SbiJwtVerifier(Collections.singletonList(root), () -> Instant.parse("2020-01-01T00:00:00Z"));
        SbiJwtVerifier late = new SbiJwtVerifier(Collections.singletonList(root), () -> Instant.parse("2050-01-01T00:00:00Z"));
        assertEquals("CERT_EXPIRED_OR_NOT_YET_VALID", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> early.verify(jwt))));
        assertEquals("CERT_EXPIRED_OR_NOT_YET_VALID", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> late.verify(jwt))));
    }

    @Test
    void anchorsAreMandatoryAndDevelopmentModeIsExplicit() {
        assertThrows(IllegalArgumentException.class, () -> new SbiJwtVerifier(Collections.<java.security.cert.X509Certificate>emptyList(), Instant::now));
        assertTrue(SbiJwtVerifier.disabledForDevelopment().isDisabled());
    }

    @Test
    void endToEndSignedCaptureResponseParses() throws Exception {
        Map<String, Object> payload = AndroidTestSupport.capturePayload("T0123456789", "2026-10-19T10:00:05Z", new byte[]{9, 8, 7});
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", MiniJson.write(payload));
        SbiCaptureResult r = liveness.android.SbiIntentBridge.parseCaptureResponse(AndroidTestSupport.captureResponseFor(jwt, "0"), verifier(ROOT_CERT));
        assertArrayEquals(new byte[]{9, 8, 7}, r.image());
        String forged = sign(ROGUE_CERT, ROGUE_KEY, "RS256", MiniJson.write(payload));
        assertTrue(assertThrows(liveness.device.DeviceException.class, () -> liveness.android.SbiIntentBridge.parseCaptureResponse(
                AndroidTestSupport.captureResponseFor(forged, "0"), verifier(ROOT_CERT))).getMessage().startsWith("SBI_SIGNATURE_INVALID"));
    }

    @Test
    void acceptsMultiHopCertificateChain() throws Exception {
        String jwt = signChain(Arrays.asList(CHAIN_LEAF_CERT, CHAIN_INTER_CERT), CHAIN_LEAF_KEY, "RS256", "{\"ok\":true}");
        assertEquals("{\"ok\":true}", verifier(CHAIN_ROOT_CERT).verify(jwt));
    }

    @Test
    void rejectsNonCaIntermediateInChain() throws Exception {
        String jwt = signChain(Arrays.asList(NON_CA_CHILD_CERT, NON_CA_CERT), NON_CA_CHILD_KEY, "RS256", "{}");
        assertEquals("NON_CA_INTERMEDIATE", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(CHAIN_ROOT_CERT).verify(jwt))));
    }

    @Test
    void rejectsLeafCertWithInvalidKeyUsage() throws Exception {
        String jwt = sign(WRONG_KU_CERT, WRONG_KU_KEY, "RS256", "{}");
        assertEquals("INVALID_KEY_USAGE", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(CHAIN_ROOT_CERT).verify(jwt))));
    }

    @Test
    void rejectsExpiredIntermediateCertificate() throws Exception {
        String jwt = signChain(Arrays.asList(CHAIN_LEAF_CERT, CHAIN_INTER_CERT), CHAIN_LEAF_KEY, "RS256", "{}");
        java.security.cert.X509Certificate root = SbiJwtVerifier.parseCertificate(Base64.getDecoder().decode(CHAIN_ROOT_CERT));
        SbiJwtVerifier late = new SbiJwtVerifier(Collections.singletonList(root), () -> Instant.parse("2050-01-01T00:00:00Z"));
        assertEquals("CERT_EXPIRED_OR_NOT_YET_VALID", reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> late.verify(jwt))));
    }

    @Test
    void verifiesValidNestedDigitalId() throws Exception {
        String didJwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{\"serial\":\"SN123\"}");
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{\"digitalId\":\"" + didJwt + "\"}");
        assertEquals("{\"digitalId\":\"" + didJwt + "\"}", verifier(ROOT_CERT).verify(jwt));
    }

    @Test
    void rejectsTamperedNestedDigitalId() throws Exception {
        String badDidJwt = sign(ROGUE_CERT, ROGUE_KEY, "RS256", "{\"serial\":\"SN123\"}");
        String jwt = sign(DEVICE_CERT, DEVICE_KEY, "RS256", "{\"digitalId\":\"" + badDidJwt + "\"}");
        assertTrue(reason(assertThrows(SbiJwtVerifier.VerificationException.class, () -> verifier(ROOT_CERT).verify(jwt))).startsWith("NESTED_DIGITAL_ID_INVALID"));
    }
}
