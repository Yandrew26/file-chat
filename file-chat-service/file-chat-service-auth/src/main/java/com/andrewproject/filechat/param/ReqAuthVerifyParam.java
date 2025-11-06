package com.andrewproject.filechat.param;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ReqAuthVerifyParam {

    private String authStr;

    private String authId;

    private String ip;

    private String uri;
}
