package com.stepxadvance.backscreentoolbox;

import java.util.*;
import java.util.regex.*;

/** Only the latest individual message is supplied here. Never persist or log its content. */
public final class MessageCard {
    private static final Pattern KEY=Pattern.compile("验证码|校验码|动态码|一次性密码|验证代码|your\\s+code|verification\\s+code|security\\s+code|login\\s+code|one[- ]time(?:\\s+(?:password|code))?|passcode|\\botp\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN=Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9]{4,8}(?![A-Za-z0-9])");
    private static final Set<String> WORDS=new HashSet<>(Arrays.asList("code","your","please","valid","expires","expire","minutes","minute","login","verify","security","password","within","never","share","with","this","from","use","enter","hidden","redacted","content","message","private","device","screen","access","request","account"));
    public final String source,code,key;
    public final long posted,expires;
    public MessageCard(String source,String code,String key,long posted,long expires){this.source=source;this.code=code;this.key=key;this.posted=posted;this.expires=expires;}
    public static String code(String text){
        if(text==null||text.length()>4096)return "";
        Matcher keys=KEY.matcher(text);int best=Integer.MAX_VALUE;String result="";
        while(keys.find()){
            Matcher tokens=TOKEN.matcher(text);
            while(tokens.find()){
                if(tokens.start()<keys.end()&&tokens.end()>keys.start())continue;
                String token=tokens.group();if(WORDS.contains(token.toLowerCase(Locale.ROOT)))continue;
                boolean after=tokens.start()>=keys.end();
                String between=after?text.substring(keys.end(),tokens.start()):text.substring(tokens.end(),keys.start());
                String gap=between.replaceAll("[\\s\\p{Punct}，。：；（）【】「」“”]","").toLowerCase(Locale.ROOT);
                if(!gap.matches("(?:|是|为|为您|为你|是您的|是你的|为您的|为你的|is|isyour|isthe|code|codeis)"))continue;
                int distance=after?tokens.start()-keys.end():keys.start()-tokens.end()+8;
                if(distance<0||distance>24)continue;
                if(distance<best){best=distance;result=token;}
            }
        }
        return result;
    }
    public static String source(String text,String fallback){
        if(text!=null){Matcher m=Pattern.compile("[【\\[]([^】\\]\\r\\n]{1,18})[】\\]]").matcher(text);if(m.find()){String s=m.group(1);Matcher keyword=KEY.matcher(text);int start=keyword.find()?keyword.start():text.length();if(m.start()<start&&m.start()<20&&!s.equals(code(text))&&!s.matches("(?i).*(?:验证码|校验码|otp|code).*"))return clean(s,18);}}
        return clean(fallback,24);
    }
    public static String clean(String s,int max){if(s==null)return "";String value=s.replaceAll("[\\p{Cntrl}\\p{Cf}]","").trim();return value.length()>max?value.substring(0,max):value;}
    public boolean alive(long now){return now<expires;}
}
