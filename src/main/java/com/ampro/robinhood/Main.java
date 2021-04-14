package com.ampro.robinhood;


import com.ampro.robinhood.endpoint.quote.data.TickerQuote;
import com.ampro.robinhood.throwables.TickerNotFoundException;

public class Main {




    public static void main(String [] args) throws TickerNotFoundException {
        RobinhoodApi api = new RobinhoodApi();

        TickerQuote msft = api.getQuoteByTicker("AAPL");
        //Print out some details
        System.out.println( msft.getSymbol() + "\n" + msft.getAskPrice() );



    }
}
