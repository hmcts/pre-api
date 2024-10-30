package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.regex.Pattern;

public final class RegexPatterns {

    private RegexPatterns() {
    }

    public static final Pattern PATTERN_1 = Pattern.compile(
        "^(?:S28[_\\s-]+)?(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?:\\d{5})?" 
        + "(?:[A-Za-z]+[-_\\s]+)?"  
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?(?:-\\d{4})?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG|ORI)?(?<versionNumber>\\d*)?[-_\\s]*"  
        + "(?:[A-Za-z0-9]+)?"
        + "(?:\\.(?i)(mp4|raw))?$"  
    );
    
    public static final Pattern PATTERN_2 = Pattern.compile(
        "^(?<court>[A-Za-z]+)?[-_\\s]*"            
        + "(?<date>\\d{6})?[-_\\s]*"               
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"        
        + "(?<exhibitRef>[A-Za-z0-9]+)?[-_\\s]*"   
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*" 
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?-?(?<versionNumber>\\d*)?" 
        + "(?:\\.(?i)(mp4|raw|RAW))?$"             
    );

    public static final Pattern PATTERN_3 = Pattern.compile(
        "^(?<court>[A-Za-z]+)[-_\\s]*" 
        + "(?<date>\\d{6})[-_\\s]*" 
        + "(?<urn>[A-Za-z0-9]+)-CP[-_\\s]*" 
        + "(?<exhibitRef>[A-Za-z0-9-]+)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+(?:-[A-Za-z]+)*)[-_\\s]*" 
        + "(?<witnessFirstName>[A-Za-z]+(?:-[A-Za-z]+)?)[-_\\s]*" 
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d*)?" 
        + "(?:\\.(?i)(mp4|raw))?$" 
    );
    public static final Pattern PATTERN_4 = Pattern.compile(
        "^(?<court>[A-Za-z]+)[-_\\s]*" 
        + "(?<date>\\d{6})[-_\\s]*" 
        + "(?<urn>[A-Za-z0-9]+)[-._\\s]*" 
        + "(?<exhibitRef>[A-Za-z0-9-]+)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d*)?" 
        + "(?:\\.(?i)(mp4|raw))?$" 
    );

    public static final Pattern PATTERN_5 = Pattern.compile(
        "^(?<date>\\d{2}-\\d{2}-\\d{4})[-_\\s]*" 
        + "(?:\\d{4})[-_\\s]*"
        + "(?<court>[A-Za-z]+)[-_\\s]*" 
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]" 
        + "(?<exhibitRef>[A-Za-z0-9]*)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d*)?" 
        + "(?:\\.(?i)(mp4|raw))?$" 
    );

    public static final Pattern PATTERN_6 = Pattern.compile(
        "^(?<court>[A-Za-z]+)[-_\\s]*"         
        + "(?<date>\\d{6})[-_\\s]*"            
        + "(?<witnessFirstName>[A-Za-z-]+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG)?(?<versionNumber>\\d*)?[-_\\s]*"  
        + "(?:\\.(?i)(mp4|raw))?$"              
    );
    
}
