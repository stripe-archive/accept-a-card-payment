require 'capybara/rspec'
require 'selenium-webdriver'

Capybara.server_host = Socket.ip_address_list.detect(&:ipv4_private?).ip_address

Capybara.register_driver :chrome do |app|
  opts = {browser: :chrome, url: ENV.fetch('SELENIUM_URL', 'http://localhost:4444/wd/hub')}
  Capybara::Selenium::Driver.new(app, **opts)
end

Capybara.javascript_driver = :chrome
Capybara.default_driver = :chrome
Capybara.default_max_wait_time = 10

module CapybaraHelpers
  SERVER_URL = ENV.fetch('SERVER_URL', 'http://localhost:4242')

  def server_url(path)
    url = URI(SERVER_URL)
    url.path = path
    url
  end
end

RSpec.configure do |config|
  config.include CapybaraHelpers
end
