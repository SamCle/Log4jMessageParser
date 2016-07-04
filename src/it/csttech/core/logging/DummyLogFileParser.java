package it.csttech.core.logging;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import it.csttech.core.data.Page;
import it.csttech.core.data.PageImpl;


public class DummyLogFileParser implements LogFileParser
{

    private static final Set<LogMessage> dummyMessages;

    static
    {
        List<String> messages = new LinkedList<>();
        messages.add("Chuck Norris doesn't read books. He stares them down until he gets the information he wants.");
        messages.add("Chuck Norris does not sleep. He waits.");
        messages.add("Chuck Norris can slam a revolving door.");
        long counter = 1;
        dummyMessages = Sets.newHashSet(new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       messages),
                                        new LogMessage(counter++, new Date(), "WARNING", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("There is no theory of evolution. Just a list of creatures Chuck Norris has allowed to live.")),
                                        new LogMessage(counter++, new Date(), "TRACE", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Outer space exists because it's afraid to be on the same planet with Chuck Norris.")),
                                        new LogMessage(counter++, new Date(), "DEBUG", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris does not sleep. He waits.")),
                                        new LogMessage(counter++, new Date(), "ERROR", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris counted to infinity - twice.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("When Chuck Norris does a pushup, he isn't lifting himself up, he's pushing the Earth down.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris is so fast, he can run around the world and punch himself in the back of the head.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris' hand is the only hand that can beat a Royal Flush.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris can lead a horse to water AND make it drink.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris doesn’t wear a watch. HE decides what time it is.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris can slam a revolving door.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris does not get frostbite. Chuck Norris bites frost.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris and Superman once fought each other on a bet. The loser had to start wearing his underwear on the outside of his pants.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Some magicans can walk on water, Chuck Norris can swim through land.")),
                                        new LogMessage(counter++, new Date(), "INFO", "Thread-01", "StaticLogGenerator",
                                                       Collections.singletonList("Chuck Norris once kicked a horse in the chin. Its decendants are known today as Giraffes.);")));
    }

    @Override
    public Page<LogMessage> nextPage(long currentMessage, long pageSize)
    {
        List<LogMessage> result = new LinkedList<>();

        long counter = currentMessage + 1;
        Iterator<LogMessage> loop = Iterables.cycle(dummyMessages).iterator();
        while (loop.hasNext())
        {
            LogMessage source = loop.next();
            result.add(new LogMessage(counter++, new Date(), source.getLogLevel(), source.getThreadName(), source.getLoggerName(), source.getFullMessage()));

            if (result.size() == pageSize)
                break;
        }

        PageImpl<LogMessage> page = new PageImpl<>();
        page.setData(result);
        page.setOffset(currentMessage);
        page.setPageSize(pageSize);
        page.setCurrentPage(0);
        page.setTotalPages(1);
        page.setTotalCount(result.size());
        return page;
    }

    @Override
    public Page<LogMessage> prevPage(long currentMessage, long pageSize)
    {
        return nextPage(currentMessage, pageSize);
    }

    @Override
    //Filtro: Cerca
    public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        return nextPage(1, 1);
    }

    @Override
    public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        return nextPage(currentMessage, pageSize);
    }

    @Override
    //Filtro: Filtra
    public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        return nextPage(currentMessage, pageSize);
    }

    @Override
    public Page<LogMessage> filterPrev(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        return nextPage(currentMessage, pageSize);
    }

    @Override
    public boolean hasTimestamp()
    {
        return true;
    }

    @Override
    public boolean hasLogLevel()
    {
        return true;
    }

    @Override
    public boolean hasThreadName()
    {
        return true;
    }

    @Override
    public boolean hasLoggerName()
    {
        return true;
    }

    @Override
    public boolean hasMessage()
    {
        return true;
    }

}
