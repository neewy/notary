FROM trufflesuite/ganache-cli:latest
COPY ./ethereum/entrypoint.sh /eth/entrypoint.sh
RUN chmod +x /eth/entrypoint.sh
ENTRYPOINT ["/eth/entrypoint.sh"]